package de.hpi.swa.analysis;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.analysis.grouping.GroupingStrategy;
import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.analysis.heuristics.Heuristic;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;
import java.util.*;
import java.util.stream.Collectors;

public class AnalysisEngine {

    // Map from Key Class -> List of Heuristics for that key type
    private final Map<Class<? extends GroupKey>, List<Heuristic.KeyHeuristic<?>>> keyHeuristics = new HashMap<>();
    private final List<Heuristic.ItemHeuristic> itemHeuristics = new ArrayList<>();

    public AnalysisEngine() {
    }

    public void registerItemHeuristic(Heuristic.ItemHeuristic heuristic) {
        itemHeuristics.add(heuristic);
    }

    public <K extends GroupKey> void registerKeyHeuristic(Class<K> type, Heuristic.KeyHeuristic<K> heuristic) {
        keyHeuristics.computeIfAbsent(type, k -> new ArrayList<>()).add(heuristic);
    }

    public List<ResultGroup> analyze(List<RunResult> results, Pool pool, GroupingStrategy groupingStrategy) {
        if (results.isEmpty())
            return Collections.emptyList();

        Map<RunResult, GroupKey> keyMap = new HashMap<>();
        for (RunResult r : results) {
            keyMap.put(r, createKey(r, pool, groupingStrategy));
        }

        Map<GroupKey, List<RunResult>> groups = new HashMap<>();
        for (var entry : keyMap.entrySet()) {
            groups.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        List<ResultGroup> scoredGroups = new ArrayList<>();
        Map<GroupKey, Map<String, Double>> keyScoreCache = new HashMap<>();

        for (var entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<RunResult> items = entry.getValue();

            Map<String, Double> groupScores = getKeyScoresRecursively(key, keyScoreCache);
            double groupTotalScore = groupScores.values().stream().mapToDouble(Double::doubleValue).sum();

            List<ScoredRunResult> sortedItems = items.stream()
                    .map(r -> {
                        Map<String, Double> itemScores = scoreItem(r);
                        double itemTotalScore = itemScores.values().stream().mapToDouble(Double::doubleValue).sum();
                        return ScoredRunResult.from(r, itemTotalScore, itemScores, groupScores, key);
                    })
                    .sorted(Comparator.comparingDouble(ScoredRunResult::score).reversed())
                    .collect(Collectors.toList());

            scoredGroups.add(new ResultGroup(key, sortedItems, groupTotalScore, groupScores));
        }

        scoredGroups.sort(Comparator.comparingDouble(ResultGroup::score).reversed());

        return scoredGroups;
    }

    private Map<String, Double> getKeyScoresRecursively(GroupKey key,
            Map<GroupKey, Map<String, Double>> cache) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Map<String, Double> scores = new HashMap<>();

        if (key instanceof GroupKey.Composite c) {
            for (GroupKey sub : c.parts()) {
                Map<String, Double> subScores = getKeyScoresRecursively(sub, cache);
                subScores.forEach((k, v) -> scores.merge(k, v, Double::sum));
            }
        } else {
            List<Heuristic.KeyHeuristic<?>> heuristics = keyHeuristics.get(key.getClass());
            if (heuristics != null) {
                for (Heuristic.KeyHeuristic<?> h : heuristics) {
                    double s = getScore(key, h);
                    scores.put(h.getName(), s);
                }
            }
        }

        cache.put(key, scores);
        return scores;
    }

    @SuppressWarnings("unchecked")
    private double getScore(GroupKey key, Heuristic.KeyHeuristic<?> h) {
        // Safe cast because we registered it with the correct type
        Heuristic.KeyHeuristic<GroupKey> typedH = (Heuristic.KeyHeuristic<GroupKey>) h;
        return typedH.score(key);
    }

    private Map<String, Double> scoreItem(RunResult item) {
        Map<String, Double> scores = new HashMap<>();
        for (var h : itemHeuristics) {
            scores.put(h.getName(), h.score(item));
        }
        return scores;
    }

    private GroupKey createKey(RunResult r, Pool pool, GroupingStrategy groupingStrategy) {
        return switch (groupingStrategy) {
            case GroupingStrategy.NoGroups s -> new GroupKey.Generic("All");
            case GroupingStrategy.CompositeGroups c -> {
                var k1 = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                var k2 = GroupKey.PathHash.from(r.getTrace(), pool);
                var k3 = GroupKey.OutputShape.from(r.getTrace());
                var k4 = GroupKey.ExceptionType.from(r.getTrace());
                yield new GroupKey.Composite(List.of(k1, k2, k3, k4));
            }
        };
    }
}
