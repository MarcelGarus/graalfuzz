package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.ScoringSpec;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.List;

public final class Scorer {

    public static final String GROUP_SCORE_AGG = "GroupScore";

    public static void computeGroupScores(ResultGroup<?, ?> node, ScoringSpec scoring, Materializer materializer) {
        node.forEachGroup(group -> {
            List<ColumnDef<?>> keyColumns = group.keyColumns();
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
                    if (!keyColumns.contains(keySource)) {
                        continue;
                    }
                }

                if (!group.results().isEmpty()) {
                    RunResult anyRow = group.results().get(0);
                    Object value = materializer.materialize(anyRow, heuristic);
                    if (value instanceof Number num) {
                        score += num.doubleValue() * weight;
                        totalWeight += weight;
                    }
                } else if (!group.children().isEmpty()) {
                    var firstChild = group.children().values().iterator().next();
                    if (!firstChild.results().isEmpty()) {
                        RunResult anyRow = firstChild.results().get(0);
                        Object value = materializer.materialize(anyRow, heuristic);
                        if (value instanceof Number num) {
                            score += num.doubleValue() * weight;
                            totalWeight += weight;
                        }
                    }
                }
            }

            double normalizedScore = totalWeight > 0 ? score / totalWeight : 0.0;
            group.aggregations().put(GROUP_SCORE_AGG, normalizedScore);
        });
    }
}
