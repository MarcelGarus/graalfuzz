package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.FilterSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Filterer {

    public static <K extends GroupKey> void filterGroups(ResultGroup<K, ?> node, FilterSpec.GroupFilter filter,
            Materializer materializer) {
        List<K> keys = new ArrayList<>(getChildKeys(node));
        List<K> toRemove = new ArrayList<>();

        for (K key : keys) {
            ResultGroup<?, K> child = getChild(node, key);
            if (child != null) {
                filterGroups(child, filter, materializer);

                if (!FilterSpec.testGroup(filter, child)) {
                    toRemove.add(key);
                }
            }
        }

        Map<K, ResultGroup<?, K>> rawChildren = node.children();
        for (K key : toRemove) {
            rawChildren.remove(key);
        }
    }

    private static <K extends GroupKey> List<K> getChildKeys(ResultGroup<K, ?> node) {
        Map<K, ResultGroup<?, K>> rawChildren = node.children();
        return new ArrayList<>(rawChildren.keySet());
    }

    private static <K extends GroupKey> ResultGroup<?, K> getChild(ResultGroup<K, ?> node, K key) {
        Map<K, ResultGroup<?, K>> rawChildren = node.children();
        return rawChildren.get(key);
    }
}
