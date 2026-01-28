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

        record Root() implements GroupKey {
            @Override
            public String toString() {
                return "<root>";
            }
        }

        record Single<T>(ColumnDef<T> column, T value) implements GroupKey {
            @Override
            public String toString() {
                return column.name() + "=" + value;
            }
        }

        record Composite(List<Part<?>> parts) implements GroupKey {

            public record Part<T>(ColumnDef<T> column, T value) {
                @Override
                public String toString() {
                    return column.name() + "=" + value;
                }
            }

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

        static Composite composite(Composite.Part<?>... parts) {
            return Composite.of(parts);
        }

        static <T> Composite.Part<T> part(ColumnDef<T> column, T value) {
            return new Composite.Part<>(column, value);
        }
    }

    public record ResultGroup<ChildKey extends GroupKey, Key extends GroupKey>(
            GroupingSpec groupingSpec,
            Key groupKey,
            ResultGroup<Key, ? extends GroupKey> parent,
            List<RunResult> results,
            Map<ChildKey, ResultGroup<? extends GroupKey, ChildKey>> children,
            Map<String, Object> aggregations,
            Map<RunResult, Map<ColumnDef<?>, Object>> projectedRowData,
            Map<ColumnDef<?>, Object> projectedGroupData) {

        public ResultGroup {
            List<RunResult> _r = results == null ? new ArrayList<>() : results;
            Map<ChildKey, ResultGroup<?, ChildKey>> _c = children == null ? new LinkedHashMap<>() : children;
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

        public ResultGroup(GroupingSpec groupingSpec, Key groupKey, ResultGroup<Key, ?> parent) {
            this(groupingSpec, groupKey, parent, new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        public static <K extends GroupKey> ResultGroup<K, GroupKey.Root> root() {
            return new ResultGroup<K, GroupKey.Root>(null, new GroupKey.Root(), null);
        }

        public <C extends GroupKey> ResultGroup<C, ChildKey> addChildForKey(ChildKey keyVal, GroupingSpec childSpec) {
            if (!children.isEmpty()) {
                GroupingSpec existing = children.values().iterator().next().groupingSpec();
                if (!Objects.equals(existing, childSpec)) {
                    throw new IllegalStateException(
                            "All children of a ResultGroup must use the same grouping spec. Expected " + existing
                                    + " but got " + childSpec);
                }
            }

            ResultGroup<?, ChildKey> child = children.computeIfAbsent(keyVal,
                    k -> new ResultGroup<C, ChildKey>(childSpec, keyVal, this));

            // Safe since we only fill children with the correct type (in this function)
            @SuppressWarnings("unchecked")
            ResultGroup<C, ChildKey> typedChild = (ResultGroup<C, ChildKey>) child;
            return typedChild;
        }

        public void forEachGroup(ConsumerWithNode c) {
            c.accept(this);
            children.values().forEach(n -> n.forEachGroup(c));
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        @FunctionalInterface
        public interface ConsumerWithNode {
            void accept(ResultGroup<?, ?> node);
        }

        public List<ColumnDef<?>> allKeyColumns() {
            var keys = keyColumns();
            if (parent == null) {
                return keys;
            }
            var parentKeys = parent.allKeyColumns();
            List<ColumnDef<?>> allKeys = new ArrayList<>(parentKeys);
            allKeys.addAll(keys);
            return allKeys;
        }

        public List<ColumnDef<?>> keyColumns() {
            if (groupingSpec == null) {
                return List.of();
            }
            return switch (groupingSpec) {
                case GroupingSpec.Single<?> groupSpec -> List.of(groupSpec.column());
                case GroupingSpec.Composite groupSpec -> groupSpec.columns();
                case GroupingSpec.Hierarchical groupSpec -> groupSpec.columns();
            };
        }

        public LinkedHashMap<ColumnDef<?>, Object> allGroupKeys() {
            LinkedHashMap<ColumnDef<?>, Object> map = new LinkedHashMap<>();
            if (parent != null) {
                map.putAll(parent.allGroupKeys());
            }
            if (groupingSpec != null && groupKey != null) {
                switch (groupingSpec) {
                    case GroupingSpec.Single<?> singleGroupSpec ->
                        map.put(singleGroupSpec.column(), ((GroupKey.Single<?>) groupKey).column());
                    case GroupingSpec.Composite compositeGroupSpec -> {
                        var cols = compositeGroupSpec.columns();
                        addCompositeGroupKeyParts(map, cols);
                    }
                    case GroupingSpec.Hierarchical hierarchicalGroupSpec -> {
                        var cols = hierarchicalGroupSpec.columns();
                        addCompositeGroupKeyParts(map, cols);
                    }
                }
            }
            return map;
        }

        public List<RunResult> topResults(int n) {
            return results.subList(0, Math.min(n, results.size()));
        }

        private void addCompositeGroupKeyParts(LinkedHashMap<ColumnDef<?>, Object> map, List<ColumnDef<?>> cols) {
            var parts = ((GroupKey.Composite) groupKey).parts();
            for (int i = 0; i < cols.size(); i++) {
                map.put(cols.get(i), parts.get(i).value());
            }
        }
    }

    public static class GroupBuilder {
        public static ResultGroup<?, GroupKey.Root> buildTree(List<RunResult> rows, List<GroupingSpec> groupBySpecs,
                Materializer materializer) {
            ResultGroup<?, GroupKey.Root> root = ResultGroup.root();

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
                case GroupingSpec.Single<?> groupSpec -> {
                    ColumnDef<?> column = groupSpec.column();
                    if (column instanceof ColumnDef.AggregationRef) {
                        throw new IllegalStateException(
                                "Cannot use AggregationRef in Single grouping for RunResult grouping. " +
                                        "Use Hierarchical grouping instead.");
                    }
                    var keyVal = materializer.materialize(row, column);
                    var typedParent = (ResultGroup<GroupKey.Single<?>, ?>) parent;
                    yield typedParent.addChildForKey(
                            new GroupKey.Single<>((ColumnDef<Object>) column, keyVal), groupSpec);
                }
                case GroupingSpec.Composite groupSpec -> {
                    List<GroupKey.Composite.Part<?>> parts = new ArrayList<>();
                    for (ColumnDef<?> col : groupSpec.columns()) {
                        if (col instanceof ColumnDef.AggregationRef) {
                            throw new IllegalStateException(
                                    "Cannot use AggregationRef in Composite grouping for RunResult grouping. " +
                                            "Use Hierarchical grouping instead.");
                        }
                        var val = materializer.materialize(row, col);
                        parts.add(createPart(col, val));
                    }
                    var compositeKey = new GroupKey.Composite(parts);
                    var typedParent = (ResultGroup<GroupKey.Composite, ?>) parent;
                    yield typedParent.addChildForKey(compositeKey, groupSpec);
                }
                case GroupingSpec.Hierarchical groupSpec -> {
                    // For hierarchical, we only use the bottom level for initial RunResult grouping
                    GroupingSpec.Hierarchical.Level bottomLevel = groupSpec.bottomLevel();
                    List<GroupKey.Composite.Part<?>> parts = new ArrayList<>();
                    for (ColumnDef<?> col : bottomLevel.columns()) {
                        if (col instanceof ColumnDef.AggregationRef) {
                            throw new IllegalStateException(
                                    "Cannot use AggregationRef in bottom level of Hierarchical grouping. " +
                                            "Bottom level groups RunResults directly.");
                        }
                        var val = materializer.materialize(row, col);
                        parts.add(createPart(col, val));
                    }
                    var compositeKey = new GroupKey.Composite(parts);
                    var typedParent = (ResultGroup<GroupKey.Composite, ?>) parent;
                    yield typedParent.addChildForKey(compositeKey, groupSpec);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private static <T> GroupKey.Composite.Part<T> createPart(ColumnDef<T> col, Object val) {
            return new GroupKey.Composite.Part<>(col, (T) val);
        }
    }
}