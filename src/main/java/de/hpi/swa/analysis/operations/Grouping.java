package de.hpi.swa.analysis.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.generator.Runner.RunResult;

public final class Grouping {

    public sealed interface GroupKey {
        record Single<T>(ColumnDef<T> column, T value) implements GroupKey {
            @Override
            public String toString() {
                return column.name() + "=" + value;
            }
        }

        record Part<T>(ColumnDef<T> column, T value) {
            @Override
            public String toString() {
                return column.name() + "=" + value;
            }
        }

        record Composite(List<Part<?>> parts) implements GroupKey {

            public Composite {
                parts = List.copyOf(parts);
            }

            public static Composite of(Part<?>... parts) {
                return new Composite(Arrays.asList(parts));
            }

            @Override
            public String toString() {
                return parts.stream()
                        .map(Part::toString)
                        .collect(Collectors.joining(" | "));
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (!(o instanceof Composite that))
                    return false;
                return Objects.equals(parts, that.parts);
            }

            @Override
            public int hashCode() {
                return Objects.hash(parts);
            }
        }

        static <T> Single<T> single(ColumnDef<T> column, T value) {
            return new Single<>(column, value);
        }

        static Composite composite(Part<?>... parts) {
            return Composite.of(parts);
        }

        static <T> Part<T> part(ColumnDef<T> column, T value) {
            return new Part<>(column, value);
        }
    }

    public record ResultGroup<K, P>(
            GroupingSpec groupingSpec,
            K groupKey,
            ResultGroup<P, ?> parent,
            List<RunResult> results,
            Map<K, ResultGroup<?, K>> children,
            Map<String, Object> aggregations,
            Map<RunResult, Map<ColumnDef<?>, Object>> projectedRowData,
            Map<ColumnDef<?>, Object> projectedGroupData) {

        public ResultGroup {
            List<RunResult> _r = results == null ? new ArrayList<>() : results;
            Map<K, ResultGroup<?, K>> _c = children == null ? new LinkedHashMap<>() : children;
            Map<String, Object> _a = aggregations == null ? new LinkedHashMap<>() : aggregations;
            Map<RunResult, Map<ColumnDef<?>, Object>> _prd = projectedRowData == null ? new LinkedHashMap<>()
                    : projectedRowData;
            Map<ColumnDef<?>, Object> _pgd = projectedGroupData == null ? new LinkedHashMap<>() : projectedGroupData;
            results = _r;
            children = _c;
            aggregations = _a;
            projectedRowData = _prd;
            projectedGroupData = _pgd;
        }

        public ResultGroup(GroupingSpec groupingSpec) {
            this(groupingSpec, null, null, new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        public static ResultGroup<Void, Void> root() {
            return new ResultGroup<>(null);
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public List<RunResult> topResults(int n) {
            return results.subList(0, Math.min(n, results.size()));
        }

        @SuppressWarnings("unchecked")
        public <C> ResultGroup<C, K> child(K keyVal, GroupingSpec childSpec) {
            if (!children.isEmpty()) {
                GroupingSpec existing = children.values().iterator().next().groupingSpec();
                if (!Objects.equals(existing, childSpec)) {
                    throw new IllegalStateException(
                            "All children of a ResultGroup must use the same grouping spec. Expected " + existing
                                    + " but got " + childSpec);
                }
            }
            return (ResultGroup<C, K>) children.computeIfAbsent(keyVal, kv -> new ResultGroup<>(childSpec, keyVal,
                    this, new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>()));
        }

        public void forEachGroup(ConsumerWithNode c) {
            c.accept(this);
            children.values().forEach(n -> n.forEachGroup(c));
        }

        @FunctionalInterface
        public interface ConsumerWithNode {
            void accept(ResultGroup<?, ?> node);
        }

        public LinkedHashMap<ColumnDef<?>, Object> allGroupKeys() {
            LinkedHashMap<ColumnDef<?>, Object> map = new LinkedHashMap<>();
            if (parent != null) {
                map.putAll(parent.allGroupKeys());
            }
            if (groupingSpec != null && groupKey != null) {
                switch (groupingSpec) {
                    case GroupingSpec.Single<?> s -> map.put(s.column(), groupKey);
                    case GroupingSpec.Composite c -> {
                        if (groupKey instanceof GroupKey.Composite composite) {
                            for (GroupKey.Part<?> part : composite.parts()) {
                                map.put(part.column(), part.value());
                            }
                        }
                    }
                }
            }
            return map;
        }

        public List<ColumnDef<?>> keyColumns() {
            if (groupingSpec == null) {
                return List.of();
            }
            return switch (groupingSpec) {
                case GroupingSpec.Single<?> s -> List.of(s.column());
                case GroupingSpec.Composite c -> c.columns();
            };
        }
    }

    public static class GroupBuilder {
        public static ResultGroup<Void, Void> buildTree(List<RunResult> rows, List<GroupingSpec> groupBySpecs,
                Materializer materializer) {
            ResultGroup<Void, Void> root = ResultGroup.root();

            for (RunResult row : rows) {
                ResultGroup<?, ?> currentGroup = root;

                for (GroupingSpec spec : groupBySpecs) {
                    currentGroup = addToGroup(row, currentGroup, spec, materializer);
                }

                currentGroup.results().add(row);
            }

            return root;
        }

        @SuppressWarnings("unchecked")
        private static ResultGroup<?, ?> addToGroup(RunResult row, ResultGroup<?, ?> parent,
                GroupingSpec spec, Materializer materializer) {
            return switch (spec) {
                case GroupingSpec.Single<?> s -> {
                    Object keyVal = materializer.materialize(row, s.column());
                    ResultGroup<Object, Object> typedParent = (ResultGroup<Object, Object>) parent;
                    yield typedParent.child(keyVal, s);
                }
                case GroupingSpec.Composite c -> {
                    List<GroupKey.Part<?>> parts = new ArrayList<>();
                    for (ColumnDef<?> col : c.columns()) {
                        Object val = materializer.materialize(row, col);
                        parts.add(createPart(col, val));
                    }
                    GroupKey.Composite compositeKey = new GroupKey.Composite(parts);
                    ResultGroup<GroupKey.Composite, Object> typedParent = (ResultGroup<GroupKey.Composite, Object>) parent;
                    yield typedParent.child(compositeKey, c);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private static <T> GroupKey.Part<T> createPart(ColumnDef<T> col, Object val) {
            return new GroupKey.Part<>(col, (T) val);
        }
    }
}