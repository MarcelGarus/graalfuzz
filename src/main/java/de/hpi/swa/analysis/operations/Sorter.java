package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupSortSpec;
import de.hpi.swa.analysis.query.SortSpec;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Sorter {

    public static void sortItems(ResultGroup<?, ?> node, List<SortSpec<?>> sortSpecs, Materializer materializer) {
        node.forEachGroup(group -> {
            List<RunResult> items = group.results();
            if (items.size() > 1) {
                items.sort(buildItemComparator(sortSpecs, materializer));
            }
        });
    }

    private static Comparator<RunResult> buildItemComparator(List<SortSpec<?>> sortSpecs, Materializer materializer) {
        Comparator<RunResult> result = (a, b) -> 0;

        for (SortSpec<?> spec : sortSpecs) {
            ColumnDef<?> column = spec.column();
            boolean ascending = spec.ascending();

            Comparator<RunResult> cmp = (r1, r2) -> {
                Object v1 = materializer.materialize(r1, column);
                Object v2 = materializer.materialize(r2, column);
                int c = compareValues(v1, v2);
                return ascending ? c : -c;
            };

            result = result.thenComparing(cmp);
        }

        return result;
    }

    public static <T> void sortGroups(ResultGroup<T, ?> node, List<GroupSortSpec> sortSpecs) {
        if (!node.children().isEmpty()) {
            var rawChildren = node.children();

            var entries = new ArrayList<>(rawChildren.entrySet());
            entries.sort((e1, e2) -> compareGroups(e1.getValue(), e2.getValue(), sortSpecs));

            rawChildren.clear();
            for (var entry : entries) {
                rawChildren.put(entry.getKey(), entry.getValue());
            }

            for (var entry : entries) {
                sortGroups(entry.getValue(), sortSpecs);
            }
        }
    }

    private static int compareGroups(ResultGroup<?, ?> g1, ResultGroup<?, ?> g2, List<GroupSortSpec> sortSpecs) {
        for (GroupSortSpec spec : sortSpecs) {
            Object v1 = g1.aggregations().get(spec.aggregationName());
            Object v2 = g2.aggregations().get(spec.aggregationName());

            int c = spec.compare(v1, v2);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static int compareValues(Object v1, Object v2) {
        if (v1 == null && v2 == null)
            return 0;
        if (v1 == null)
            return -1;
        if (v2 == null)
            return 1;

        if (v1 instanceof Comparable<?> c1 && v2 instanceof Comparable<?>) {
            try {
                return ((Comparable<Object>) c1).compareTo(v2);
            } catch (ClassCastException e) {
                return v1.toString().compareTo(v2.toString());
            }
        }
        return v1.toString().compareTo(v2.toString());
    }
}
