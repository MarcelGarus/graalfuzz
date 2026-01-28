package de.hpi.swa.analysis.operations;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.AggregationSpec;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.generator.Runner.RunResult;

public final class Aggregator {

    /**
     * Compute aggregations for all groups in the tree (non-hierarchical).
     * Uses bottom-up aggregation: leaf groups aggregate from RunResults,
     * parent groups aggregate from children.
     */
    public static Map<ResultGroup<?, ?>, Map<String, Object>> compute(ResultGroup<?, ?> root,
            List<AggregationSpec<?, ?>> aggregations, Materializer materializer) {
        Map<ResultGroup<?, ?>, Map<String, Object>> out = new IdentityHashMap<>();
        computeRec(root, aggregations, materializer, out);
        return out;
    }

    private static List<RunResult> computeRec(ResultGroup<?, ?> node, List<AggregationSpec<?, ?>> aggs,
            Materializer mat,
            Map<ResultGroup<?, ?>, Map<String, Object>> out) {

        List<RunResult> all = new ArrayList<>();
        boolean isLeaf = node.children().isEmpty();

        if (isLeaf) {
            all.addAll(node.results());
        } else {
            for (var child : node.children().values()) {
                all.addAll(computeRec(child, aggs, mat, out));
            }
        }

        Map<String, Object> aggMap = new LinkedHashMap<>();
        for (AggregationSpec<?, ?> a : aggs) {
            Object v = computeAggregation(a, node, all, isLeaf, mat);
            aggMap.put(a.name(), v);
        }

        node.aggregations().clear();
        node.aggregations().putAll(aggMap);

        out.put(node, aggMap);
        return all;
    }

    private static Object computeAggregation(
            AggregationSpec<?, ?> agg,
            ResultGroup<?, ?> node,
            List<RunResult> allResults,
            boolean isLeaf,
            Materializer mat) {

        ColumnDef<?> column = agg.column();

        if (column instanceof ColumnDef.AggregationRef<?> aggRef) {
            return aggregateFromChildAggregations(agg, node, aggRef.aggregationName());
        }

        if (agg instanceof AggregationSpec.DistributiveAggregation<?, ?> dist && !isLeaf) {
            return aggregateFromChildResultGroups(dist, node);
        }

        return aggregateFromRunResults(agg, allResults, mat);
    }

    /**
     * Aggregate from RunResults using the materializer.
     * Only works for non-AggregationRef columns.
     */
    static <I, O> O aggregateFromRunResults(AggregationSpec<I, O> aggregation, List<RunResult> rows,
            Materializer materializer) {

        if (aggregation.column() instanceof ColumnDef.AggregationRef) {
            throw new IllegalArgumentException(
                    "Cannot aggregate from RunResults using AggregationRef column: " + aggregation.name());
        }

        List<I> values = rows.stream()
                .map(r -> materializer.materialize(r, aggregation.column()))
                .toList();
        return aggregation.aggregatorFn().apply(values);
    }

    /**
     * Aggregate from child groups using their already-computed aggregations.
     * Works for distributive aggregations.
     */
    @SuppressWarnings({ "unchecked" })
    static <I, O> O aggregateFromChildResultGroups(AggregationSpec.DistributiveAggregation<I, O> aggregation,
            ResultGroup<?, ?> node) {
        List<O> childValues = (List<O>) node.children().values().stream()
                .map(child -> child.aggregations().get(aggregation.name()))
                .toList();
        return aggregation.combinerFn().apply(childValues);
    }

    @SuppressWarnings({ "unchecked" })
    static <I, O> O aggregateFromChildAggregations(
            AggregationSpec<I, O> aggregation,
            ResultGroup<?, ?> node,
            String sourceAggregationName) {

        List<I> valuesToAggregate = (List<I>) node.children().values().stream()
                .map(child -> child.aggregations().get(sourceAggregationName))
                .filter(v -> v != null)
                .toList();

        return aggregation.aggregatorFn().apply(valuesToAggregate);
    }
}
