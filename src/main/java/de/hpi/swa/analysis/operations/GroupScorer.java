package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.ScoringSpec;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.List;

public final class GroupScorer {

    public static final String GROUP_SCORE_AGG = "GroupScore";

    public static void computeGroupScores(ResultGroup<?, ?> node, ScoringSpec scoring, Materializer materializer) {
        node.forEachGroup(group -> {
            List<ColumnDef<?>> keyColumns = group.allKeyColumns();
            if (keyColumns.isEmpty()) {
                group.aggregations().put(GROUP_SCORE_AGG, 0.0);
                return;
            }

            double score = 0.0;
            double totalWeight = 0.0;

            for (var entry : scoring.keyHeuristicWeights().entrySet()) {
                ColumnDef<?> heuristic = entry.getKey();
                double weight = entry.getValue();

                if (heuristic instanceof ColumnDef.PreparableKeyColumn<?, ?> keyHeuristic) {
                    ColumnDef<?> keySource = keyHeuristic.keySource();
                    // Check if any of the group's current keys match the keyHeuristic. If so we can
                    // take any run result and retrieve its value and it will be the same for all
                    // rows in this group.
                    if (!keyColumns.contains(keySource)) {
                        continue;
                    }
                }

                RunResult anyRow = findFirstResultDFS(group);
                if (anyRow != null) {
                    Object value = materializer.materialize(anyRow, heuristic);
                    if (value instanceof Number num) {
                        score += num.doubleValue() * weight;
                        totalWeight += weight;
                    }
                }
            }

            double normalizedScore = totalWeight > 0 ? score / totalWeight : 0.0;
            group.aggregations().put(GROUP_SCORE_AGG, normalizedScore);
        });
    }

    private static RunResult findFirstResultDFS(ResultGroup<?, ?> group) {
        if (!group.results().isEmpty()) {
            return group.results().get(0);
        }

        for (var child : group.children().values()) {
            RunResult result = findFirstResultDFS(child);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
}
