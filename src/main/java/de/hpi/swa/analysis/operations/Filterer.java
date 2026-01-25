package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.FilterSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Filterer {

    public static <T> void filterGroups(ResultGroup<T, ?> node, FilterSpec.GroupFilter filter,
            Materializer materializer) {
        List<T> keys = new ArrayList<>(getChildKeys(node));
        List<T> toRemove = new ArrayList<>();

        for (T key : keys) {
            ResultGroup<?, T> child = getChild(node, key);
            if (child != null) {
                filterGroups(child, filter, materializer);

                if (!FilterSpec.testGroup(filter, child)) {
                    toRemove.add(key);
                }
            }
        }

        Map<T, ResultGroup<?, T>> rawChildren = node.children();
        for (T key : toRemove) {
            rawChildren.remove(key);
        }
    }

    private static <T> List<T> getChildKeys(ResultGroup<T, ?> node) {
        Map<T, ResultGroup<?, T>> rawChildren = node.children();
        return new ArrayList<>(rawChildren.keySet());
    }

    private static <T> ResultGroup<?, T> getChild(ResultGroup<T, ?> node, T key) {
        Map<T, ResultGroup<?, T>> rawChildren = node.children();
        return rawChildren.get(key);
    }
}
