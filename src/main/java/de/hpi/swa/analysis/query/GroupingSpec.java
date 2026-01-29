package de.hpi.swa.analysis.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public sealed interface GroupingSpec {

    List<ColumnDef<?>> columns();

    /**
     * Simple single-column grouping.
     */
    record Single<T>(ColumnDef<T> column) implements GroupingSpec {
        @Override
        public List<ColumnDef<?>> columns() {
            return List.of(column);
        }

        @Override
        public final String toString() {
            return column.name();
        }
    }

    /**
     * Composite (flat) grouping: multiple columns treated as a single key. But
     * groups can't be defined recursively.
     */
    record Composite(List<ColumnDef<?>> columns) implements GroupingSpec {
        public Composite {
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Composite grouping requires at least one column");
            }
            columns = List.copyOf(columns);
        }

        public static Composite of(ColumnDef<?>... columns) {
            return new Composite(Arrays.asList(columns));
        }

        @Override
        public List<ColumnDef<?>> columns() {
            return columns;
        }

        @Override
        public final String toString() {
            return String.join(" | ", columns.stream().map(ColumnDef::name).toList());
        }
    }

    /**
     * Hierarchical grouping with post-aggregation re-grouping.
     * 
     * Uses a flat list of levels, processed from LAST to FIRST:
     * - Last level: must use non-AggregationRef columns (grouped from RunResults)
     * - Earlier levels: must use only AggregationRef columns (grouped from child
     * groups)
     * 
     * Each level has:
     * - columns: what to group by at this level
     * - aggregations: computed after grouping at this level
     * - groupFilter: applied after aggregation at this level
     * 
     * Example for inputShapeOutputTypeTable:
     * levels = [
     * Level(columns=[OutputTypes_AggRef], aggregations=[inputShapesUnion,
     * sumCounts], filter=none),
     * Level(columns=[InputShape], aggregations=[distinctSet(OutputType), count],
     * filter=none)
     * ]
     * 
     * Execution order (last level first):
     * 1. Group RunResults by InputShape
     * 2. Aggregate: distinctSet(OutputType) -> OutputTypes, count -> RowCount
     * 3. Group resulting groups by OutputTypes (AggregationRef)
     * 4. Aggregate: inputShapesUnion, sum(RowCount)
     * 5. Return final groups
     */
    record Hierarchical(List<Level> levels) implements GroupingSpec {
        public record Level(
                List<ColumnDef<?>> columns,
                List<AggregationSpec<?, ?>> aggregations,
                Optional<FilterSpec.GroupFilter> groupFilter,
                List<GroupSortSpec> groupSort) {

            public Level {
                if (columns.isEmpty()) {
                    throw new IllegalArgumentException("Level requires at least one column");
                }
                columns = List.copyOf(columns);
                aggregations = aggregations != null ? List.copyOf(aggregations) : List.of();
                groupFilter = groupFilter != null ? groupFilter : Optional.empty();
                groupSort = groupSort != null ? List.copyOf(groupSort) : List.of();
            }

            public boolean hasAggregations() {
                return !aggregations.isEmpty();
            }

            public boolean hasGroupFilter() {
                return groupFilter.isPresent();
            }

            public boolean hasGroupSort() {
                return !groupSort.isEmpty();
            }

            public boolean usesAggregationRef() {
                return columns.stream().anyMatch(c -> c instanceof ColumnDef.AggregationRef);
            }

            public static Builder builder() {
                return new Builder();
            }

            public static class Builder {
                private List<ColumnDef<?>> columns = List.of();
                private List<AggregationSpec<?, ?>> aggregations = List.of();
                private Optional<FilterSpec.GroupFilter> groupFilter = Optional.empty();
                private List<GroupSortSpec> groupSort = List.of();

                public Builder columns(ColumnDef<?>... cols) {
                    this.columns = List.of(cols);
                    return this;
                }

                public Builder aggregations(AggregationSpec<?, ?>... aggs) {
                    this.aggregations = List.of(aggs);
                    return this;
                }

                public Builder groupFilter(FilterSpec.GroupFilter filter) {
                    this.groupFilter = Optional.of(filter);
                    return this;
                }

                public Builder groupSort(GroupSortSpec... sorts) {
                    this.groupSort = List.of(sorts);
                    return this;
                }

                public Level build() {
                    return new Level(columns, aggregations, groupFilter, groupSort);
                }
            }
        }

        public Hierarchical {
            if (levels == null || levels.isEmpty()) {
                throw new IllegalArgumentException("Hierarchical grouping requires at least one level");
            }
            levels = List.copyOf(levels);

            Level lastLevel = levels.get(levels.size() - 1);
            for (ColumnDef<?> col : lastLevel.columns()) {
                if (col instanceof ColumnDef.AggregationRef) {
                    throw new IllegalArgumentException(
                            "Last level of Hierarchical grouping cannot use AggregationRef columns. " +
                                    "Last level groups RunResults directly.");
                }
            }

            for (int i = 0; i < levels.size() - 1; i++) {
                Level level = levels.get(i);
                for (ColumnDef<?> col : level.columns()) {
                    if (!(col instanceof ColumnDef.AggregationRef)) {
                        throw new IllegalArgumentException(
                                "Level " + i + " of Hierarchical grouping must use only AggregationRef columns. " +
                                        "Found: " + col.name() + ". Only the last level can use regular columns.");
                    }
                }
            }
        }

        @Override
        public List<ColumnDef<?>> columns() {
            List<ColumnDef<?>> allCols = new ArrayList<>();
            for (Level level : levels) {
                allCols.addAll(level.columns());
            }
            return allCols;
        }

        public Level topLevel() {
            return levels.get(0);
        }

        public Level bottomLevel() {
            return levels.get(levels.size() - 1);
        }

        public int levelCount() {
            return levels.size();
        }

        public boolean isMultiLevel() {
            return levels.size() > 1;
        }

        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder("Hierarchical(");
            for (int i = 0; i < levels.size(); i++) {
                if (i > 0)
                    sb.append(" -> ");
                Level level = levels.get(i);
                sb.append("[");
                sb.append(String.join(" | ", level.columns().stream().map(ColumnDef::name).toList()));
                if (level.hasAggregations()) {
                    sb.append(" => ").append(level.aggregations().size()).append(" aggs");
                }
                sb.append("]");
            }
            sb.append(")");
            return sb.toString();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final List<Level> levels = new ArrayList<>();

            public Builder level(Level level) {
                this.levels.add(level);
                return this;
            }

            public Hierarchical build() {
                return new Hierarchical(levels);
            }
        }
    }

    static <T> Single<T> single(ColumnDef<T> column) {
        return new Single<>(column);
    }

    static Composite composite(ColumnDef<?>... columns) {
        return Composite.of(columns);
    }

    static Hierarchical hierarchical(Hierarchical.Level... levels) {
        return new Hierarchical(List.of(levels));
    }

    static Hierarchical hierarchical(List<Hierarchical.Level> levels) {
        return new Hierarchical(levels);
    }
}
