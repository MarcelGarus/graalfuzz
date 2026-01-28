package de.hpi.swa.analysis.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Query(
        // 1. Column definitions (base & computed)
        List<ColumnDef<?>> columns,

        // 2. Grouping specifications (ordered, first = top-level)
        // Each GroupingSpec can be a single column or composite (multiple columns =
        // flat key). In case of hierarchical grouping, there must be exactly one
        // GroupingSpec of type Hierarchical.
        List<GroupingSpec> groupBy,

        // 3. Filtering Items (runs)
        Optional<FilterSpec.RowFilter> itemFilter,

        // 4. Deduplication (remove duplicate rows by column)
        List<ColumnDef<?>> deduplicateBy,

        // 5. Aggregations to compute for each group
        List<AggregationSpec<?, ?>> aggregations,

        // 6. Filtering Groups (applied after aggregations)
        Optional<FilterSpec.GroupFilter> groupFilter,

        // 7. Sorting Items (within each group)
        List<SortSpec<?>> itemSort,

        // 8. Projection of item columns (rows)
        ProjectionSpec itemProjection,

        // 9. Drill-through control (how many results per group to show)
        DrillSpec drillSpec,

        // 10. Sorting Groups (by aggregation values or group score)
        List<GroupSortSpec> groupSort,

        // 11. Projection of group columns
        ProjectionSpec groupProjection,

        // 12. Scoring configuration for weighted heuristics
        ScoringSpec scoringConfig,

        // 13. Group limit (how many child groups to show per parent group)
        GroupLimitSpec groupLimit) {
    public Query {
        columns = List.copyOf(columns);
        groupBy = List.copyOf(groupBy);

        long hierarchicalCount = groupBy.stream()
                .filter(spec -> spec instanceof GroupingSpec.Hierarchical)
                .count();
        if (hierarchicalCount > 1) {
            throw new IllegalArgumentException(
                    "Only one Hierarchical grouping spec is allowed per query. " +
                            "Use multiple levels within a single Hierarchical spec instead.");
        }
        if (hierarchicalCount == 1 && groupBy.size() > 1) {
            throw new IllegalArgumentException(
                    "When using Hierarchical grouping, it must be the only groupBy spec. " +
                            "Found " + groupBy.size() + " groupBy specs.");
        }

        itemFilter = Objects.requireNonNull(itemFilter);
        for (var c : deduplicateBy) {
            if (c instanceof ColumnDef.AggregationRef) {
                throw new IllegalArgumentException("AggregationRef cannot be used in deduplicateBy");
            }
        }
        deduplicateBy = List.copyOf(deduplicateBy);
        aggregations = List.copyOf(aggregations);
        groupFilter = Objects.requireNonNull(groupFilter);
        for (SortSpec<?> spec : itemSort) {
            if (spec.column() instanceof ColumnDef.AggregationRef) {
                throw new IllegalArgumentException("AggregationRef cannot be used in itemSort");
            }
        }
        itemSort = List.copyOf(itemSort);
        itemProjection = itemProjection == null ? new ProjectionSpec(List.of()) : itemProjection;
        for (var c : itemProjection.selectedColumns()) {
            if (c instanceof ColumnDef.AggregationRef) {
                throw new IllegalArgumentException("AggregationRef cannot be used in itemProjection");
            }
        }
        groupSort = List.copyOf(groupSort);
        groupProjection = groupProjection == null ? new ProjectionSpec(List.of()) : groupProjection;
        scoringConfig = scoringConfig == null ? ScoringSpec.defaultConfig() : scoringConfig;
        groupLimit = groupLimit == null ? new GroupLimitSpec.None() : groupLimit;
    }

    public boolean hasHierarchicalGrouping() {
        return groupBy.stream().anyMatch(spec -> spec instanceof GroupingSpec.Hierarchical);
    }

    public Optional<GroupingSpec.Hierarchical> getHierarchicalGrouping() {
        return groupBy.stream()
                .filter(spec -> spec instanceof GroupingSpec.Hierarchical)
                .map(spec -> (GroupingSpec.Hierarchical) spec)
                .findFirst();
    }

    /* Convenience builders (minimal fluent help) */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        List<ColumnDef<?>> columns = new ArrayList<>();
        List<GroupingSpec> groupByList = new ArrayList<>();
        Optional<FilterSpec.RowFilter> itemFilter = Optional.empty();
        List<ColumnDef<?>> deduplicateBy = new ArrayList<>();
        List<AggregationSpec<?, ?>> aggs = new ArrayList<>();
        Optional<FilterSpec.GroupFilter> groupFilter = Optional.empty();
        List<SortSpec<?>> itemSort = new ArrayList<>();
        ProjectionSpec itemProjection = new ProjectionSpec(List.of());
        DrillSpec drillSpec = new DrillSpec.LeafsOnly(10);
        List<GroupSortSpec> groupSort = new ArrayList<>();
        ProjectionSpec groupProjection = new ProjectionSpec(List.of());
        ScoringSpec scoringConfig = null; // will use default
        GroupLimitSpec groupLimit = null; // will use default

        public Builder columns(ColumnDef<?>... defs) {
            Collections.addAll(this.columns, defs);
            return this;
        }

        public Builder groupBy(ColumnDef<?>... cols) {
            for (var col : cols) {
                this.groupByList.add(new GroupingSpec.Single<>(col));
            }
            return this;
        }

        public Builder groupBy(GroupingSpec spec) {
            this.groupByList.add(spec);
            return this;
        }

        public Builder groupByComposite(ColumnDef<?>... cols) {
            this.groupByList.add(new GroupingSpec.Composite(List.of(cols)));
            return this;
        }

        public Builder itemFilter(FilterSpec.RowFilter e) {
            this.itemFilter = Optional.of(e);
            return this;
        }

        public Builder deduplicateBy(ColumnDef<?>... cols) {
            for (var c : cols) {
                if (c instanceof ColumnDef.AggregationRef) {
                    throw new IllegalArgumentException("AggregationRef cannot be used in deduplicateBy");
                }
            }

            Collections.addAll(this.deduplicateBy, cols);
            return this;
        }

        public Builder aggregations(AggregationSpec<?, ?>... a) {
            Collections.addAll(this.aggs, a);
            return this;
        }

        public Builder groupFilter(FilterSpec.GroupFilter e) {
            this.groupFilter = Optional.of(e);
            return this;
        }

        public Builder itemSort(SortSpec<?>... s) {
            for (SortSpec<?> spec : s) {
                if (spec.column() instanceof ColumnDef.AggregationRef) {
                    throw new IllegalArgumentException("AggregationRef cannot be used in itemSort");
                }
            }

            Collections.addAll(this.itemSort, s);
            return this;
        }

        public Builder itemProjection(ColumnDef<?>... cols) {
            for (var c : cols) {
                if (c instanceof ColumnDef.AggregationRef) {
                    throw new IllegalArgumentException("AggregationRef cannot be used in itemProjection");
                }
            }

            this.itemProjection = new ProjectionSpec(List.of(cols));
            return this;
        }

        public Builder drill(DrillSpec d) {
            this.drillSpec = d;
            return this;
        }

        public Builder groupSort(GroupSortSpec... s) {
            Collections.addAll(this.groupSort, s);
            return this;
        }

        public Builder groupSortByAggregation(String aggregationName, boolean ascending) {
            this.groupSort.add(new GroupSortSpec(aggregationName, ascending));
            return this;
        }

        public Builder groupProjection(ColumnDef<?>... cols) {
            this.groupProjection = new ProjectionSpec(List.of(cols));
            return this;
        }

        public Builder scoring(ScoringSpec config) {
            this.scoringConfig = config;
            return this;
        }

        public Builder groupLimit(GroupLimitSpec limit) {
            this.groupLimit = limit;
            return this;
        }

        public Query build() {
            return new Query(
                    columns,
                    groupByList,
                    itemFilter,
                    deduplicateBy,
                    aggs,
                    groupFilter,
                    itemSort,
                    itemProjection,
                    drillSpec,
                    groupSort,
                    groupProjection,
                    scoringConfig,
                    groupLimit);
        }
    }
}