package de.hpi.swa.analysis.operations;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.AggregationSpec;
import de.hpi.swa.generator.Runner.RunResult;

public final class Aggregator {

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
            Object v = switch (a) {
                case AggregationSpec.DistributiveAggregation<?, ?> dist when !isLeaf ->
                    aggregateFromChildResultGroups(dist, node, mat);
                default -> aggregateFromRunResults(a, all, mat);
            };
            aggMap.put(a.name(), v);
        }

        node.aggregations().clear();
        node.aggregations().putAll(aggMap);

        out.put(node, aggMap);
        return all;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <I, O> O aggregateFromRunResults(AggregationSpec<I, O> aggregation, List<RunResult> rows,
            Materializer materializer) {
        return switch (aggregation) {
            case AggregationSpec.DistributiveAggregation(var name, var column, var aggregatorFn, var combinerFn) -> {
                List<I> values = rows.stream()
                        .map(r -> materializer.materialize(r, column))
                        .toList();
                yield (O) ((BiFunction) aggregatorFn).apply(values, materializer);
            }
            case AggregationSpec.RawAggregation(var name, var column, var aggregatorFn) -> {
                List<I> values = rows.stream()
                        .map(r -> materializer.materialize(r, column))
                        .toList();
                yield (O) ((BiFunction) aggregatorFn).apply(values, materializer);
            }
        };
    }

    @SuppressWarnings({ "unchecked" })
    static <I, O> O aggregateFromChildResultGroups(AggregationSpec.DistributiveAggregation<I, O> aggregation,
            ResultGroup<?, ?> node, Materializer materializer) {
        List<O> childValues = (List<O>) node.children().values().stream()
                .map(child -> child.aggregations().get(aggregation.name()))
                .toList();
        return aggregation.combinerFn().apply(childValues, materializer);
    }
}
