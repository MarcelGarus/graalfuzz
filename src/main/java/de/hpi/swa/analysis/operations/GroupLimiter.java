package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.GroupLimitSpec;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GroupLimiter {

    private GroupLimiter() {
    }

    public static void applyGroupLimit(ResultGroup<?, ?> node, GroupLimitSpec limitSpec) {
        switch (limitSpec) {
            case GroupLimitSpec.None() -> {
            }
            case GroupLimitSpec.LeafsOnly(int maxGroups) -> {
                applyLimitLeafsOnly(node, maxGroups);
            }
            case GroupLimitSpec.All(int maxGroups) -> {
                applyLimitAll(node, maxGroups);
            }
        }
    }

    private static void applyLimitLeafsOnly(ResultGroup<?, ?> node, int maxGroups) {
        for (var child : node.children().values()) {
            applyLimitLeafsOnly(child, maxGroups);
        }

        if (!node.children().isEmpty()) {
            boolean childrenAreLeaves = node.children().values().stream()
                    .allMatch(ResultGroup::isLeaf);

            if (childrenAreLeaves) {
                limitChildren(node, maxGroups);
            }
        }
    }

    private static void applyLimitAll(ResultGroup<?, ?> node, int maxGroups) {
        for (var child : node.children().values()) {
            applyLimitAll(child, maxGroups);
        }

        if (!node.children().isEmpty()) {
            limitChildren(node, maxGroups);
        }
    }

    private static <K> void limitChildren(ResultGroup<K, ?> node, int maxGroups) { 
        Map<K, ResultGroup<?, K>> children = node.children();

        if (maxGroups == -1 || children.size() <= maxGroups) {
            return;
        }

        Map<K, ResultGroup<?, K>> limitedChildren = new LinkedHashMap<>();
        int count = 0;

        for (Map.Entry<K, ResultGroup<?, K>> entry : children.entrySet()) {
            if (count >= maxGroups) {
                break;
            }
            limitedChildren.put(entry.getKey(), entry.getValue());
            count++;
        }

        children.clear();
        children.putAll(limitedChildren);
    }
}
