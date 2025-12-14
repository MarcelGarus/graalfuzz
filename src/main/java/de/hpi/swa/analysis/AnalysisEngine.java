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

        // 1. Generate Keys
        Map<RunResult, GroupKey> keyMap = new HashMap<>();
        for (RunResult r : results) {
            keyMap.put(r, createKey(r, pool, groupingStrategy));
        }

        // 2. Build Context
        AnalysisContext context = new AnalysisContext(results, new ArrayList<>(keyMap.values()));

        // 3. Group Results
        Map<GroupKey, List<RunResult>> groups = new HashMap<>();
        for (var entry : keyMap.entrySet()) {
            groups.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // 4. Score Groups and Items
        List<ResultGroup> scoredGroups = new ArrayList<>();
        Map<GroupKey, Map<String, Double>> keyScoreCache = new HashMap<>();

        for (var entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<RunResult> items = entry.getValue();

            // Score Key (Memoized)
            Map<String, Double> groupScores = getKeyScoresRecursively(key, context, keyScoreCache);
            double groupTotalScore = groupScores.values().stream().mapToDouble(Double::doubleValue).sum();

            // Score Items
            List<ScoredRunResult> sortedItems = items.stream()
                    .map(r -> {
                        Map<String, Double> itemScores = scoreItem(r, context);
                        double itemTotalScore = itemScores.values().stream().mapToDouble(Double::doubleValue).sum();
                        return ScoredRunResult.from(r, itemTotalScore, itemScores, groupScores, key);
                    })
                    .sorted(Comparator.comparingDouble(ScoredRunResult::score).reversed())
                    .collect(Collectors.toList());

            scoredGroups.add(new ResultGroup(key, sortedItems, groupTotalScore, groupScores));
        }

        // 5. Sort Groups
        scoredGroups.sort(Comparator.comparingDouble(ResultGroup::score).reversed());

        return scoredGroups;
    }

    private Map<String, Double> getKeyScoresRecursively(GroupKey key, AnalysisContext context,
            Map<GroupKey, Map<String, Double>> cache) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Map<String, Double> scores = new HashMap<>();

        if (key instanceof GroupKey.Composite c) {
            for (GroupKey sub : c.parts()) {
                Map<String, Double> subScores = getKeyScoresRecursively(sub, context, cache);
                subScores.forEach((k, v) -> scores.merge(k, v, Double::sum));
            }
        } else {
            List<Heuristic.KeyHeuristic<?>> heuristics = keyHeuristics.get(key.getClass());
            if (heuristics != null) {
                for (Heuristic.KeyHeuristic<?> h : heuristics) {
                    double s = getScore(key, context, h);
                    scores.put(h.getName(), s);
                }
            }
        }

        cache.put(key, scores);
        return scores;
    }

    @SuppressWarnings("unchecked")
    private double getScore(GroupKey key, AnalysisContext context, Heuristic.KeyHeuristic<?> h) {
        // Safe cast because we registered it with the correct type
        Heuristic.KeyHeuristic<GroupKey> typedH = (Heuristic.KeyHeuristic<GroupKey>) h;
        return typedH.score(key, context);
    }

    private Map<String, Double> scoreItem(RunResult item, AnalysisContext context) {
        Map<String, Double> scores = new HashMap<>();
        for (var h : itemHeuristics) {
            scores.put(h.getName(), h.score(item, context));
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
