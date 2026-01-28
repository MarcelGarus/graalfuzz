package de.hpi.swa.analysis.query;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public sealed interface AggregationSpec<I, O> {

    String name();

    ColumnDef<I> column();

    Function<List<I>, O> aggregatorFn();

    // Distributive aggregations can be combined from children's aggregated values
    record DistributiveAggregation<I, O>(
            String name,
            ColumnDef<I> column,
            Function<List<I>, O> aggregatorFn,
            Function<List<O>, O> combinerFn) implements AggregationSpec<I, O> {
    }

    // Raw aggregations need access to all RunResults
    record RawAggregation<I, O>(
            String name,
            ColumnDef<I> column,
            Function<List<I>, O> aggregatorFn) implements AggregationSpec<I, O> {
    }

    static <T> DistributiveAggregation<T, Integer> count(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> vals.size(),
                counts -> counts.stream().mapToInt(Integer::intValue).sum());
    }

    static <T> DistributiveAggregation<T, Integer> sum(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> vals.stream()
                        .filter(v -> v instanceof Number)
                        .mapToInt(v -> ((Number) v).intValue())
                        .sum(),
                sums -> sums.stream().mapToInt(Integer::intValue).sum());
    }

    static <T> RawAggregation<T, Integer> uniqueCount(ColumnDef<T> column, String name) {
        return new RawAggregation<>(name, column,
                vals -> (int) vals.stream().distinct().count());
    }

    static <T extends Number> RawAggregation<T, Double> avg(ColumnDef<T> column, String name) {
        return new RawAggregation<>(name, column, vals -> vals.stream()
                .filter(v -> v != null)
                .mapToDouble(v -> v.doubleValue())
                .average()
                .orElse(Double.NaN));
    }

    static <T> DistributiveAggregation<T, Integer> countNonNull(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> (int) vals.stream().filter(v -> v != null).count(),
                counts -> counts.stream().mapToInt(Integer::intValue).sum());
    }

    static <T> DistributiveAggregation<T, Integer> countIf(ColumnDef<T> column, String name,
            java.util.function.Predicate<T> predicate) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> (int) vals.stream().filter(v -> v != null && predicate.test(v)).count(),
                counts -> counts.stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * Distinct set: Like a union but it does not combined nested properties.
     */
    static <T> DistributiveAggregation<T, Set<T>> distinctSet(ColumnDef<T> column, String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> new HashSet<>(vals),
                sets -> {
                    Set<T> result = new HashSet<>();
                    for (Set<T> set : sets) {
                        result.addAll(set);
                    }
                    return result;
                });
    }

    /**
     * Property shape union: for ObjectShapes, computes a union over each property.
     * For non-object shapes (primitives), includes them as a special "__type__"
     * key.
     * Returns a map from property name to set of shapes seen for that property.
     * 
     * Example inputs:
     * {bar: int}, {bar: float} -> {bar: [int, float]}
     * {foo: {bar: int}}, {foo: {bar: string}} -> {foo: {bar: [int, string]}}
     * int, string -> {__type__: [int, string]}
     */
    static DistributiveAggregation<Shape, Map<String, Set<Shape>>> propertyShapeUnion(ColumnDef<Shape> column,
            String name) {
        return new DistributiveAggregation<>(
                name,
                column,
                vals -> {
                    Map<String, Set<Shape>> result = new LinkedHashMap<>();

                    // recurse through object shapes and record leaf primitive shapes under
                    // dotted path keys (e.g. "foo.bar")
                    java.util.function.BiConsumer<Shape.ObjectShape, String> collect = new java.util.function.BiConsumer<>() {
                        @Override
                        public void accept(Shape.ObjectShape obj, String prefix) {
                            // NOTE: Partial object access limitation:
                            // If run1 accesses {foo: int} and run2 accesses {bar: string}, this aggregation
                            // will produce {foo: [int], bar: [string]}, which looks like one object with
                            // both
                            // keys. However, no single run actually saw that combined shape. This is
                            // acceptable
                            // for understanding the overall API surface, but means we lose the information
                            // that
                            // these properties were accessed in different execution paths. To preserve
                            // this,
                            // we'd need to track "property access sets" per run and group by those sets,
                            // but
                            // that would explode the number of groups. The current approach prioritizes
                            // clarity
                            // by showing "all observed properties" rather than "per-run property
                            // combinations."
                            for (String key : obj.keys()) {
                                Shape prop = obj.at(key);
                                String path = (prefix == null || prefix.isEmpty()) ? key : prefix + "." + key;
                                if (prop == null) {
                                    continue;
                                }
                                if (prop instanceof Shape.ObjectShape child) {
                                    // Recurse into nested object
                                    accept(child, path);
                                } else {
                                    result.computeIfAbsent(path, k -> new HashSet<>()).add(prop);
                                }
                            }
                        }
                    };

                    for (Shape shape : vals) {
                        if (shape == null) {
                            continue;
                        }
                        if (shape instanceof Shape.ObjectShape objShape) {
                            collect.accept(objShape, "");
                        } else {
                            result.computeIfAbsent("__type__", k -> new HashSet<>()).add(shape);
                        }
                    }

                    return result;
                },
                maps -> {
                    Map<String, Set<Shape>> result = new LinkedHashMap<>();
                    for (Map<String, Set<Shape>> map : maps) {
                        for (Map.Entry<String, Set<Shape>> entry : map.entrySet()) {
                            result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
                        }
                    }
                    return result;
                });
    }
}
