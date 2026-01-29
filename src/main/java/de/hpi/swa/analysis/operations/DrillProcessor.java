package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.DrillSpec;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DrillProcessor {
    public static void applyDrillSpec(ResultGroup<?, ?> node, DrillSpec drillSpec) {
        switch (drillSpec) {
            case DrillSpec.None() -> {
                node.forEachGroup(group -> group.results().clear());
            }
            case DrillSpec.LeafsOnly(int topN) -> {
                node.forEachGroup(group -> {
                    if (group.isLeaf()) {
                        limitResults(group, topN);
                    } else {
                        group.results().clear();
                    }
                });
            }
            case DrillSpec.All(int topN) -> {
                propagateResultsToParentsSorted(node, topN);
            }
        }
    }

    private static void propagateResultsToParentsSorted(ResultGroup<?, ?> node, int topN) {
        for (var child : node.children().values()) {
            propagateResultsToParentsSorted(child, topN);
        }

        if (!node.isLeaf()) {
            List<RunResult> merged = mergeSortedChildResults(node, topN);
            node.results().clear();
            node.results().addAll(merged);
        } else {
            limitResults(node, topN);
        }
    }

    private static List<RunResult> mergeSortedChildResults(ResultGroup<?, ?> node, int topN) {
        List<RunResult> merged = new ArrayList<>();
        Set<RunResult> seen = new HashSet<>();

        for (RunResult r : node.results()) {
            if (seen.add(r)) {
                merged.add(r);
            }
        }

        List<List<RunResult>> childResults = new ArrayList<>();
        for (var child : node.children().values()) {
            if (!child.results().isEmpty()) {
                childResults.add(new ArrayList<>(child.results()));
            }
        }

        if (childResults.isEmpty()) {
            return limitList(merged, topN);
        }

        int[] indices = new int[childResults.size()];
        boolean hasMore = true;
        while (merged.size() < topN && hasMore) {
            hasMore = false;
            for (int i = 0; i < childResults.size() && merged.size() < topN; i++) {
                List<RunResult> childList = childResults.get(i);
                if (indices[i] < childList.size()) {
                    RunResult r = childList.get(indices[i]);
                    if (seen.add(r)) {
                        merged.add(r);
                    }
                    indices[i]++;
                    hasMore = true;
                }
            }
        }

        return limitList(merged, topN);
    }

    private static List<RunResult> limitList(List<RunResult> list, int limit) {
        if (limit == -1 || list.size() <= limit) {
            return list;
        }
        return new ArrayList<>(list.subList(0, limit));
    }

    private static void limitResults(ResultGroup<?, ?> group, int limit) {
        if (limit == -1) {
            return;
        }

        List<RunResult> kept = new ArrayList<>(group.topResults(limit));
        group.results().clear();
        group.results().addAll(kept);
    }
}
