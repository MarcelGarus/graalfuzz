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
                // Propagate results from children to parents, maintaining sort order
                // Use topN during propagation for efficiency
                propagateResultsToParentsSorted(node, topN);
            }
        }
    }

    /**
     * Propagates results from children to parents while maintaining sorted order.
     * Uses a merge strategy that takes top N from each child (already sorted),
     * then merges them in order. This is efficient because we only need topN
     * results.
     */
    private static void propagateResultsToParentsSorted(ResultGroup<?, ?> node, int topN) {
        // First, recursively process all children so they have their results ready
        for (var child : node.children().values()) {
            propagateResultsToParentsSorted(child, topN);
        }

        if (!node.isLeaf()) {
            // Merge children's sorted results maintaining order
            // Each child already has at most topN sorted results
            List<RunResult> merged = mergeSortedChildResults(node, topN);
            node.results().clear();
            node.results().addAll(merged);
        } else {
            // Leaf nodes: just limit to topN (already sorted)
            limitResults(node, topN);
        }
    }

    /**
     * Merges sorted results from children, taking top N overall.
     * Assumes children's results are already sorted by the same criteria.
     * Uses a simple merge approach that preserves relative order from sorted
     * children.
     */
    private static List<RunResult> mergeSortedChildResults(ResultGroup<?, ?> node, int topN) {
        List<RunResult> merged = new ArrayList<>();
        Set<RunResult> seen = new HashSet<>(); // For deduplication

        // Add any existing results in the node itself first (preserves original order)
        for (RunResult r : node.results()) {
            if (seen.add(r)) {
                merged.add(r);
            }
        }

        // Interleave from children to maintain balanced sampling
        // This approach takes results round-robin from children to get diversity
        List<List<RunResult>> childResults = new ArrayList<>();
        for (var child : node.children().values()) {
            if (!child.results().isEmpty()) {
                childResults.add(new ArrayList<>(child.results()));
            }
        }

        if (childResults.isEmpty()) {
            return limitList(merged, topN);
        }

        // Round-robin merge: take one from each child in turn until we have enough
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
        if (list.size() <= limit) {
            return list;
        }
        return new ArrayList<>(list.subList(0, limit));
    }

    private static void limitResults(ResultGroup<?, ?> group, int limit) {
        // Make a copy since topResults returns a sublist view backed by the same list
        List<RunResult> kept = new ArrayList<>(group.topResults(limit));
        group.results().clear();
        group.results().addAll(kept);
    }
}
