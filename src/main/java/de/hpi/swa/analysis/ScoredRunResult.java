package de.hpi.swa.analysis;

import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Value;
import de.hpi.swa.generator.Universe;
import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.generator.Trace;

import java.util.Map;

public record ScoredRunResult(
        RunResult result,
        double score,
        Map<String, Double> itemScores,
        Map<String, Double> groupScores,
        GroupKey key) {
    public static ScoredRunResult from(RunResult result, double score, Map<String, Double> itemScores,
            Map<String, Double> groupScores, GroupKey key) {
        return new ScoredRunResult(result, score, itemScores, groupScores, key);
    }

    public Value input() {
        return result.getInput();
    }

    public Universe universe() {
        return result.getUniverse();
    }

    public Trace trace() {
        return result.getTrace();
    }
}