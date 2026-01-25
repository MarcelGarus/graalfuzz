package de.hpi.swa.analysis.query;

import de.hpi.swa.analysis.operations.Materializer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public sealed interface AggregationSpec<I, O> {
    
    String name();
    ColumnDef<I> column();

    // Distributive aggregations can be combined from children's aggregated values
    record DistributiveAggregation<I, O>(
            String name,
            ColumnDef<I> column,
            BiFunction<List<I>, Materializer, O> aggregatorFn,
            BiFunction<List<O>, Materializer, O> combinerFn
    ) implements AggregationSpec<I, O> {}
    
    // Raw aggregations need access to all RunResults
    record RawAggregation<I, O>(
            String name,
            ColumnDef<I> column,
            BiFunction<List<I>, Materializer, O> aggregatorFn
    ) implements AggregationSpec<I, O> {}

    static <T> DistributiveAggregation<T, Integer> count(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
            name, 
            column, 
            (vals, m) -> vals.size(),
            (counts, m) -> counts.stream().mapToInt(Integer::intValue).sum()
        );
    }

    static <T> DistributiveAggregation<T, Integer> sum(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
            name,
            column,
            (vals, m) -> vals.stream()
                .filter(v -> v instanceof Number)
                .mapToInt(v -> ((Number) v).intValue())
                .sum(),
            (sums, m) -> sums.stream().mapToInt(Integer::intValue).sum()
        );
    }

    static <T> RawAggregation<T, Integer> uniqueCount(ColumnDef<T> column, String name) {
        return new RawAggregation<>(name, column,
                (vals, m) -> (int) vals.stream().distinct().count());
    }

    static <T extends Number> RawAggregation<T, Double> avg(ColumnDef<T> column, String name) {
        return new RawAggregation<>(name, column, (vals, m) -> vals.stream()
                .filter(v -> v != null)
                .mapToDouble(v -> v.doubleValue())
                .average()
                .orElse(Double.NaN));
    }

    static <T> DistributiveAggregation<T, Set<T>> distinctSet(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                (vals, m) -> new HashSet<>(vals),
                (sets, m) -> {
                    Set<T> result = new HashSet<>();
                    for (Set<T> set : sets) {
                        result.addAll(set);
                    }
                    return result;
                });
    }

    static <T> DistributiveAggregation<T, Integer> countNonNull(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                (vals, m) -> (int) vals.stream().filter(v -> v != null).count(),
                (counts, m) -> counts.stream().mapToInt(Integer::intValue).sum());
    }

    static <T> DistributiveAggregation<T, Integer> countIf(ColumnDef<T> column, String name,
            java.util.function.Predicate<T> predicate) {
        return new DistributiveAggregation<>(
                name,
                column,
                (vals, m) -> (int) vals.stream().filter(v -> v != null && predicate.test(v)).count(),
                (counts, m) -> counts.stream().mapToInt(Integer::intValue).sum());
    }
}
