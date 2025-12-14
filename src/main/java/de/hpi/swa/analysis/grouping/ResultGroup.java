package de.hpi.swa.analysis.grouping;

import java.util.List;
import java.util.Map;

import de.hpi.swa.analysis.ScoredRunResult;

public record ResultGroup(
        GroupKey key,
        List<ScoredRunResult> results,
        double score,
        Map<String, Double> groupScores) {

    public ResultGroup withScore(double newScore) {
        return new ResultGroup(key, results, newScore, groupScores);
    }

    public ResultGroup top(int n) {
        List<ScoredRunResult> topResults = results.subList(0, Math.min(n, results.size()));
        return new ResultGroup(key, topResults, score, groupScores);
    }
}
