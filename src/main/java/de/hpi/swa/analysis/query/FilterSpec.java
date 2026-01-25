package de.hpi.swa.analysis.query;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.operations.Materializer;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public sealed interface FilterSpec {

    sealed interface RowFilter extends FilterSpec {
        record And(RowFilter... filters) implements RowFilter {
        }

        record Or(RowFilter... filters) implements RowFilter {
        }

        record Not(RowFilter filter) implements RowFilter {
        }

        record SingleColumnFilter<T>(ColumnDef<T> column, Predicate<T> predicate) implements RowFilter {
        }

        static RowFilter.And and(RowFilter... filters) {
            return new RowFilter.And(filters);
        }

        static RowFilter.Or or(RowFilter... filters) {
            return new RowFilter.Or(filters);
        }

        static RowFilter.Not not(RowFilter filter) {
            return new RowFilter.Not(filter);
        }

        static <T> RowFilter.SingleColumnFilter<T> equals(ColumnDef<T> column, T expected) {
            return new RowFilter.SingleColumnFilter<>(column, v -> v == null ? expected == null : v.equals(expected));
        }

        static <T> RowFilter.SingleColumnFilter<T> greaterThan(ColumnDef<T> column, Comparable<T> threshold) {
            return new RowFilter.SingleColumnFilter<>(column, v -> v != null && threshold.compareTo(v) < 0);
        }

        static <T> RowFilter.SingleColumnFilter<T> lessThan(ColumnDef<T> column, Comparable<T> threshold) {
            return new RowFilter.SingleColumnFilter<>(column, v -> v != null && threshold.compareTo(v) > 0);
        }

        static <T> RowFilter.SingleColumnFilter<T> predicate(ColumnDef<T> column, Predicate<T> predicate) {
            return new RowFilter.SingleColumnFilter<>(column, predicate);
        }
    }

    sealed interface GroupFilter extends FilterSpec {
        record And(GroupFilter... filters) implements GroupFilter {
        }

        record Or(GroupFilter... filters) implements GroupFilter {
        }

        record Not(GroupFilter filter) implements GroupFilter {
        }

        record AggregationFilter<T>(String aggregationName, Predicate<T> predicate) implements GroupFilter {
        }

        record TwoAggregationFilter<T, U>(String aggName1, String aggName2, BiPredicate<T, U> predicate)
                implements GroupFilter {
        }

        static GroupFilter.And and(GroupFilter... filters) {
            return new GroupFilter.And(filters);
        }

        static GroupFilter.Or or(GroupFilter... filters) {
            return new GroupFilter.Or(filters);
        }

        static GroupFilter.Not not(GroupFilter filter) {
            return new GroupFilter.Not(filter);
        }

        static <T> GroupFilter.AggregationFilter<T> equals(String aggregationName, T expected) {
            return new GroupFilter.AggregationFilter<>(aggregationName,
                    v -> v == null ? expected == null : v.equals(expected));
        }

        static <T> GroupFilter.AggregationFilter<T> greaterThan(String aggregationName, Comparable<T> threshold) {
            return new GroupFilter.AggregationFilter<>(aggregationName, v -> v != null && threshold.compareTo(v) > 0);
        }

        static <T> GroupFilter.AggregationFilter<T> lessThan(String aggregationName, Comparable<T> threshold) {
            return new GroupFilter.AggregationFilter<>(aggregationName, v -> v != null && threshold.compareTo(v) < 0);
        }

        static <T> GroupFilter.AggregationFilter<T> predicate(String aggregationName, Predicate<T> predicate) {
            return new GroupFilter.AggregationFilter<>(aggregationName, predicate);
        }

        static <T, U> GroupFilter.TwoAggregationFilter<T, U> predicate2(String aggName1, String aggName2,
                BiPredicate<T, U> predicate) {
            return new GroupFilter.TwoAggregationFilter<>(aggName1, aggName2, predicate);
        }
    }

    static boolean testRow(RowFilter filter, RunResult result, Materializer materializer) {
        return switch (filter) {
            case RowFilter.And(RowFilter[] filters) ->
                Arrays.stream(filters).allMatch(f -> testRow(f, result, materializer));

            case RowFilter.Or(RowFilter[] filters) ->
                Arrays.stream(filters).anyMatch(f -> testRow(f, result, materializer));

            case RowFilter.Not(RowFilter f) ->
                !testRow(f, result, materializer);

            case RowFilter.SingleColumnFilter(var column, var predicate) -> {
                var value = materializer.materialize(result, column);
                @SuppressWarnings("unchecked")
                var pred = (Predicate<Object>) predicate;
                yield pred.test(value);
            }
        };
    }

    static boolean testGroup(GroupFilter filter, ResultGroup<?, ?> group) {
        Map<String, Object> aggregations = group.aggregations();
        return switch (filter) {
            case GroupFilter.And(GroupFilter[] filters) ->
                Arrays.stream(filters).allMatch(f -> testGroup(f, group));

            case GroupFilter.Or(GroupFilter[] filters) ->
                Arrays.stream(filters).anyMatch(f -> testGroup(f, group));

            case GroupFilter.Not(GroupFilter f) ->
                !testGroup(f, group);

            case GroupFilter.AggregationFilter(var aggName, var predicate) -> {
                Object value = aggregations.get(aggName);
                @SuppressWarnings("unchecked")
                var pred = (Predicate<Object>) predicate;
                yield pred.test(value);
            }

            case GroupFilter.TwoAggregationFilter(var aggName1, var aggName2, var predicate) -> {
                Object value1 = aggregations.get(aggName1);
                Object value2 = aggregations.get(aggName2);
                @SuppressWarnings("unchecked")
                var pred = (BiPredicate<Object, Object>) predicate;
                yield pred.test(value1, value2);
            }
        };
    }
}