package de.hpi.swa.analysis;

import de.hpi.swa.analysis.operations.*;
import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.FilterSpec;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.analysis.query.NamedQuery;
import de.hpi.swa.analysis.query.Query;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.*;
import java.util.stream.Collectors;

public class AnalysisEngine {

    public record MultiQueryResult(Map<String, ResultGroup<?, ?>> results, Materializer sharedMaterializer) {
        public ResultGroup<?, ?> get(String queryName) {
            return results.get(queryName);
        }

        public Set<String> queryNames() {
            return results.keySet();
        }
    }

    public static ResultGroup<?, GroupKey.Root> analyze(Query query, List<RunResult> results, Pool pool) {
        if (results.isEmpty()) {
            return ResultGroup.root();
        }

        LinkedHashSet<ColumnDef<?>> allColumns = ColumnCollector.buildColumnDefinitions(query);
        Materializer materializer = new Materializer();
        // TODO: Only prepare columns that are actually used in this query
        materializer.prepareAll(allColumns, results, pool);

        return analyzeWithMaterializer(query, results, materializer);
    }

    public static MultiQueryResult analyzeMultiple(List<NamedQuery> namedQueries, List<RunResult> results, Pool pool) {
        if (results.isEmpty() || namedQueries.isEmpty()) {
            return new MultiQueryResult(Map.of(), new Materializer());
        }

        LinkedHashSet<ColumnDef<?>> allColumns = new LinkedHashSet<>();
        for (NamedQuery nq : namedQueries) {
            // TODO: Only prepare columns that are actually used in this query
            allColumns.addAll(ColumnCollector.buildColumnDefinitions(nq.query()));
        }

        Materializer sharedMaterializer = new Materializer();
        sharedMaterializer.prepareAll(allColumns, results, pool);

        Map<String, ResultGroup<?, ?>> queryResults = new LinkedHashMap<>();
        for (NamedQuery nq : namedQueries) {
            ResultGroup<?, ?> result = analyzeWithMaterializer(nq.query(), results, sharedMaterializer);
            queryResults.put(nq.name(), result);
        }

        return new MultiQueryResult(queryResults, sharedMaterializer);
    }

    private static ResultGroup<?, GroupKey.Root> analyzeWithMaterializer(Query query, List<RunResult> results,
            Materializer materializer) {
        List<RunResult> filteredResults = results;
        if (query.itemFilter().isPresent()) {
            FilterSpec.RowFilter itemFilter = query.itemFilter().get();
            filteredResults = results.stream()
                    .filter(r -> FilterSpec.testRow(itemFilter, r, materializer))
                    .collect(Collectors.toList());
        }

        if (!query.deduplicateBy().isEmpty()) {
            filteredResults = Deduplicator.deduplicateResults(filteredResults, query.deduplicateBy(), materializer);
        }

        ResultGroup<?, GroupKey.Root> root;
        if (query.hasHierarchicalGrouping()) {
            root = processHierarchicalGrouping(query, filteredResults, materializer);
        } else {
            root = processStandardGrouping(query, filteredResults, materializer);
        }

        if (!query.itemSort().isEmpty()) {
            Sorter.sortItems(root, query.itemSort(), materializer);
        }

        if (!query.groupSort().isEmpty()) {
            Sorter.sortGroups(root, query.groupSort());
        }

        GroupLimiter.applyGroupLimit(root, query.groupLimit());

        DrillProcessor.applyDrillSpec(root, query.drillSpec());

        Projector.materializeProjections(root, query.groupProjection(), query.itemProjection(), materializer);

        return root;
    }

    private static ResultGroup<?, GroupKey.Root> processStandardGrouping(
            Query query,
            List<RunResult> results,
            Materializer materializer) {

        ResultGroup<?, GroupKey.Root> root = Grouping.GroupBuilder.buildTree(
                results,
                query.groupBy(),
                materializer);

        if (!query.aggregations().isEmpty()) {
            Aggregator.compute(root, query.aggregations(), materializer);
        }

        GroupScorer.computeGroupScores(root, query.scoringConfig(), materializer);

        if (query.groupFilter().isPresent()) {
            Filterer.filterGroups(root, query.groupFilter().get(), materializer);
        }

        return root;
    }

    /**
     * Process hierarchical grouping with post-aggregation re-grouping.
     * 
     * The algorithm processes levels from bottom (last) to top (first):
     * 1. Group RunResults by the bottom level columns
     * 2. Compute bottom level aggregations
     * 3. Apply bottom level filter
     * 4. For each higher level:
     * a. Re-group by AggregationRef columns (using child aggregations)
     * b. Compute this level's aggregations
     * c. Apply this level's filter
     * 5. Finally compute query-level aggregations on top-level groups
     */
    private static ResultGroup<GroupKey.Composite, GroupKey.Root> processHierarchicalGrouping(
            Query query,
            List<RunResult> results,
            Materializer materializer) {

        GroupingSpec.Hierarchical hierarchical = query.getHierarchicalGrouping().get();
        List<GroupingSpec.Hierarchical.Level> levels = hierarchical.levels();

        int bottomLevelIndex = levels.size() - 1;
        GroupingSpec.Hierarchical.Level bottomLevel = levels.get(bottomLevelIndex);

        var currentRoot = groupRunResultsByLevel(results, bottomLevel,
                materializer);

        if (bottomLevel.hasAggregations()) {
            Aggregator.compute(currentRoot, bottomLevel.aggregations(), materializer);
        }

        GroupScorer.computeGroupScores(currentRoot, query.scoringConfig(), materializer);
        if (bottomLevel.hasGroupFilter()) {
            filterGroupsNonRecursive(currentRoot, bottomLevel.groupFilter().get(), materializer);
        }
        List<de.hpi.swa.analysis.query.GroupSortSpec> bottomSort = bottomLevel.hasGroupSort()
                ? bottomLevel.groupSort()
                : query.groupSort();
        if (!bottomSort.isEmpty()) {
            Sorter.sortGroups(currentRoot, bottomSort);
        }

        for (int i = bottomLevelIndex - 1; i >= 0; i--) {
            GroupingSpec.Hierarchical.Level level = levels.get(i);

            currentRoot = regroupByLevel(currentRoot, level, materializer);

            if (level.hasAggregations()) {
                computeLevelAggregations(currentRoot, level.aggregations(), materializer);
            }

            GroupScorer.computeGroupScores(currentRoot, query.scoringConfig(), materializer);

            if (level.hasGroupFilter()) {
                filterGroupsNonRecursive(currentRoot, level.groupFilter().get(), materializer);
            }

            List<de.hpi.swa.analysis.query.GroupSortSpec> levelSort = level.hasGroupSort()
                    ? level.groupSort()
                    : query.groupSort();
            if (!levelSort.isEmpty()) {
                Sorter.sortGroups(currentRoot, levelSort);
            }
        }

        return currentRoot;
    }

    /**
     * Filter groups at the current level only (non-recursive).
     * Used for hierarchical grouping where each level has its own filter.
     */
    private static <K extends GroupKey> void filterGroupsNonRecursive(
            ResultGroup<K, ?> node,
            FilterSpec.GroupFilter filter,
            Materializer materializer) {

        List<K> keys = new ArrayList<>(node.children().keySet());
        List<K> toRemove = new ArrayList<>();

        for (K key : keys) {
            ResultGroup<?, K> child = node.children().get(key);
            if (child != null && !FilterSpec.testGroup(filter, child)) {
                toRemove.add(key);
            }
        }

        for (K key : toRemove) {
            node.children().remove(key);
        }
    }

    /**
     * Group RunResults by a single level (used for bottom level of hierarchy).
     */
    private static ResultGroup<GroupKey.Composite, GroupKey.Root> groupRunResultsByLevel(
            List<RunResult> rows,
            GroupingSpec.Hierarchical.Level level,
            Materializer materializer) {

        ResultGroup<GroupKey.Composite, GroupKey.Root> root = ResultGroup.root();

        for (RunResult row : rows) {
            List<GroupKey.Composite.Part<?>> parts = new ArrayList<>();
            for (ColumnDef<?> col : level.columns()) {
                Object val = materializer.materialize(row, col);
                parts.add(createPart(col, val));
            }
            GroupKey.Composite key = new GroupKey.Composite(parts);

            ResultGroup<?, GroupKey.Composite> group = root.addChildForKey(key,
                    new GroupingSpec.Composite(level.columns()));

            group.results().add(row);
        }

        return root;
    }

    /**
     * Re-group child groups by a intermediate or top level
     * that uses AggregationRef columns.
     */
    private static ResultGroup<GroupKey.Composite, GroupKey.Root> regroupByLevel(
            ResultGroup<GroupKey.Composite, GroupKey.Root> currentRoot,
            GroupingSpec.Hierarchical.Level level,
            Materializer materializer) {

        ResultGroup<GroupKey.Composite, GroupKey.Root> newRoot = ResultGroup.root();

        for (var childEntry : currentRoot.children().entrySet()) {
            // Safe because all child groups in hierarchical grouping are Composite keyed
            @SuppressWarnings("unchecked")
            var childGroup = (ResultGroup<GroupKey.Composite, GroupKey.Composite>) childEntry
                    .getValue();

            List<GroupKey.Composite.Part<?>> parts = new ArrayList<>();
            for (ColumnDef<?> col : level.columns()) {
                Object val;
                if (col instanceof ColumnDef.AggregationRef<?> aggRef) {
                    val = childGroup.aggregations().get(aggRef.aggregationName());
                } else {
                    throw new IllegalStateException(
                            "Non-bottom level columns must be AggregationRef, found: " + col.name());
                }
                parts.add(createPart(col, val));
            }
            GroupKey.Composite newKey = new GroupKey.Composite(parts);

            ResultGroup<GroupKey.Composite, GroupKey.Composite> parentGroup = newRoot.addChildForKey(newKey,
                    new GroupingSpec.Composite(level.columns()));

            parentGroup.children().put(childEntry.getKey(), childGroup);
        }

        return newRoot;
    }

    /**
     * Compute aggregations for groups at the current level.
     * Handles both regular and AggregationRef columns.
     * 
     * Note: Does NOT process children recursively - children should already be
     * processed.
     */
    private static void computeLevelAggregations(
            ResultGroup<GroupKey.Composite, GroupKey.Root> root,
            List<de.hpi.swa.analysis.query.AggregationSpec<?, ?>> aggregations,
            Materializer materializer) {

        for (var child : root.children().values()) {
            // Compute for this group only (children already processed)
            Map<String, Object> aggMap = new LinkedHashMap<>();
            for (var agg : aggregations) {
                Object val = computeSingleAggregation(child, agg, materializer);
                aggMap.put(agg.name(), val);
            }

            // Merge with existing aggregations (don't overwrite child-level aggregations)
            for (var entry : aggMap.entrySet()) {
                child.aggregations().put(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <I, O> O computeSingleAggregation(
            ResultGroup<?, ?> group,
            de.hpi.swa.analysis.query.AggregationSpec<I, O> agg,
            Materializer materializer) {

        ColumnDef<I> column = agg.column();

        if (column instanceof ColumnDef.AggregationRef<?> aggRef) {
            // For AggregationRef, collect values from children's aggregations
            if (!group.children().isEmpty()) {
                List<I> values = (List<I>) group.children().values().stream()
                        .map(child -> child.aggregations().get(aggRef.aggregationName()))
                        .filter(v -> v != null)
                        .toList();
                return agg.aggregatorFn().apply(values);
            } else {
                // No children, return empty aggregation
                return agg.aggregatorFn().apply(List.of());
            }
        } else {
            // Regular column - aggregate from RunResults
            if (!group.results().isEmpty()) {
                List<I> values = group.results().stream()
                        .map(r -> materializer.materialize(r, column))
                        .toList();
                return agg.aggregatorFn().apply(values);
                // Check if column to aggregate over is in the gropingSpec.columns of childgroup
            } else if (!group.children().isEmpty()
                    && group.children().values().iterator().next().groupingSpec() instanceof GroupingSpec.Composite comp
                    && comp.columns().contains(column)) {
                List<I> values = (List<I>) group.children().values().stream()
                        .map(child -> {
                            GroupKey.Composite key = (GroupKey.Composite) child.groupKey();
                            for (var part : key.parts()) {
                                if (part.column().equals(column)) {
                                    return part.value();
                                }
                            }
                            return null;
                        })
                        .filter(v -> v != null)
                        .toList();
                return agg.aggregatorFn().apply(values);
            } else {
                return agg.aggregatorFn().apply(List.of());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> GroupKey.Composite.Part<T> createPart(ColumnDef<T> col, Object val) {
        return new GroupKey.Composite.Part<>(col, (T) val);
    }
}
