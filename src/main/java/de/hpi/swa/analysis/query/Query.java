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
        // flat key)
        List<GroupingSpec> groupBy,

        // 3. Filtering Items (runs)
        Optional<FilterSpec.RowFilter> itemFilter,

        // 4. Aggregations to compute for each group
        List<AggregationSpec<?, ?>> aggregations,

        // 5. Filtering Groups (applied after aggregations)
        Optional<FilterSpec.GroupFilter> groupFilter,

        // 6. Sorting Items (within each group)
        List<SortSpec<?>> itemSort,

        // 7. Projection of item columns (rows)
        ProjectionSpec itemProjection,

        // 8. Drill-through control (how many results per group to show)
        DrillSpec drillSpec,

        // 9. Sorting Groups (by aggregation values or group score)
        List<GroupSortSpec> groupSort,

        // 10. Projection of group columns
        ProjectionSpec groupProjection,

        // 11. Scoring configuration for weighted heuristics
        ScoringSpec scoringConfig,

        // 12. Group limit (how many child groups to show per parent group)
        GroupLimitSpec groupLimit) {
    public Query {
        columns = List.copyOf(columns);
        groupBy = List.copyOf(groupBy);
        itemFilter = Objects.requireNonNull(itemFilter);
        aggregations = List.copyOf(aggregations);
        groupFilter = Objects.requireNonNull(groupFilter);
        itemSort = List.copyOf(itemSort);
        itemProjection = itemProjection == null ? new ProjectionSpec(List.of()) : itemProjection;
        groupSort = List.copyOf(groupSort);
        groupProjection = groupProjection == null ? new ProjectionSpec(List.of()) : groupProjection;
        scoringConfig = scoringConfig == null ? ScoringSpec.defaultConfig() : scoringConfig;
        groupLimit = groupLimit == null ? new GroupLimitSpec.None() : groupLimit;
    }

    /* Convenience builders (minimal fluent help) */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        List<ColumnDef<?>> columns = new ArrayList<>();
        List<GroupingSpec> groupByList = new ArrayList<>();
        Optional<FilterSpec.RowFilter> itemFilter = Optional.empty();
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

        public Builder aggregations(AggregationSpec<?, ?>... a) {
            Collections.addAll(this.aggs, a);
            return this;
        }

        public Builder groupFilter(FilterSpec.GroupFilter e) {
            this.groupFilter = Optional.of(e);
            return this;
        }

        public Builder itemSort(SortSpec<?>... s) {
            Collections.addAll(this.itemSort, s);
            return this;
        }

        public Builder itemProjection(ColumnDef<?>... cols) {
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