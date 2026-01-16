package de.hpi.swa.analysis;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.analysis.grouping.GroupingStrategy;
import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.analysis.heuristics.Heuristic;
import de.hpi.swa.analysis.heuristics.ItemHeuristics;
import de.hpi.swa.analysis.heuristics.KeyHeuristics;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;
import java.util.*;
import java.util.stream.Collectors;

public class AnalysisEngine {

    private static class WeightedHeuristic<T> {
        final T heuristic;
        final double weight;

        WeightedHeuristic(T heuristic, double weight) {
            this.heuristic = heuristic;
            this.weight = weight;
        }
    }

    // Map from Key Class -> List of Heuristics and their weights for that key type
    private final Map<Class<? extends GroupKey>, List<WeightedHeuristic<Heuristic.KeyHeuristic<?>>>> keyHeuristics = new HashMap<>();
    private final List<WeightedHeuristic<Heuristic.ItemHeuristic>> itemHeuristics = new ArrayList<>();

    private double groupWeightSum = 0.0;
    private double itemWeightSum = 0.0;

    public AnalysisEngine() {
        registerKeyHeuristic(GroupKey.InputShape.class, new KeyHeuristics.InputValidity(), 100.0);
        registerKeyHeuristic(GroupKey.InputShape.class, new KeyHeuristics.InputShapeSimplicity(), 10.0);
        registerKeyHeuristic(GroupKey.InputShape.class, new KeyHeuristics.OutputShapeDiversity());
        registerKeyHeuristic(GroupKey.PathHash.class, new KeyHeuristics.CoverageRarity(), 2.0);
        registerKeyHeuristic(GroupKey.PathHash.class, new KeyHeuristics.PathSimplicity());

        registerItemHeuristic(new ItemHeuristics.MinimalInput(), 5.0);
        registerItemHeuristic(new ItemHeuristics.MinimalOutput());
    }

    public void registerItemHeuristic(Heuristic.ItemHeuristic heuristic) {
        registerItemHeuristic(heuristic, 1.0);
    }

    public void registerItemHeuristic(Heuristic.ItemHeuristic heuristic, double weight) {
        itemHeuristics.add(new WeightedHeuristic<>(heuristic, weight));
        itemWeightSum += weight;
    }

    public <K extends GroupKey> void registerKeyHeuristic(Class<K> type, Heuristic.KeyHeuristic<K> heuristic) {
        registerKeyHeuristic(type, heuristic, 1.0);
    }

    public <K extends GroupKey> void registerKeyHeuristic(Class<K> type, Heuristic.KeyHeuristic<K> heuristic,
            double weight) {
        keyHeuristics.computeIfAbsent(type, k -> new ArrayList<>()).add(new WeightedHeuristic<>(heuristic, weight));
        groupWeightSum += weight;
    }

    public List<ResultGroup> analyze(List<RunResult> results, Pool pool, GroupingStrategy groupingStrategy) {
        if (results.isEmpty())
            return Collections.emptyList();

        Map<GroupKey, Set<RunResult>> groups = groupResultsByKey(results, pool, groupingStrategy);

        prepareHeuristics(results, pool);

        List<ResultGroup> scoredGroups = new ArrayList<>();
        Map<GroupKey, Map<String, Double>> keyScoreCache = new HashMap<>();

        for (var entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            Set<RunResult> items = entry.getValue();

            Map<String, Double> groupScores = getKeyScoresRecursively(key, keyScoreCache);
            double groupTotalScore = groupScores.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                    .sum();
            groupTotalScore = groupWeightSum > 0 ? groupTotalScore / groupWeightSum : groupTotalScore;

            List<ScoredRunResult> sortedItems = items.stream()
                    .map(r -> {
                        Map<String, Double> itemScores = scoreItem(r);
                        double itemTotalScore = itemScores.values().stream()
                                .mapToDouble(Double::doubleValue)
                                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                                .sum();
                        itemTotalScore = itemWeightSum > 0 ? itemTotalScore / itemWeightSum : itemTotalScore;
                        return ScoredRunResult.from(r, itemTotalScore, itemScores, groupScores, key);
                    })
                    .sorted(Comparator.comparingDouble(ScoredRunResult::score).reversed())
                    .collect(Collectors.toList());

            scoredGroups.add(new ResultGroup(key, sortedItems, groupTotalScore, groupScores));
        }

        scoredGroups.sort(Comparator.comparingDouble(ResultGroup::score).reversed());

        return scoredGroups;
    }

    private Map<GroupKey, Set<RunResult>> groupResultsByKey(List<RunResult> results, Pool pool,
            GroupingStrategy groupingStrategy) {
        Map<RunResult, GroupKey> keyMap = new HashMap<>();
        for (RunResult r : results) {
            keyMap.put(r, createKey(r, pool, groupingStrategy));
        }

        Map<GroupKey, Set<RunResult>> groups = new HashMap<>();
        for (var runResult : keyMap.keySet()) {
            var groupKey = keyMap.get(runResult);
            groups.computeIfAbsent(groupKey, k -> new HashSet<>()).add(runResult);
        }
        return groups;
    }

    private void prepareHeuristics(List<RunResult> results, Pool pool) {
        for (var hs : keyHeuristics.values()) {
            for (var h : hs) {
                h.heuristic.prepare(results, pool);
            }
        }
        for (var h : itemHeuristics) {
            h.heuristic.prepare(results, pool);
        }
    }

    private Map<String, Double> getKeyScoresRecursively(GroupKey key,
            Map<GroupKey, Map<String, Double>> cache) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Map<String, Double> scores = new HashMap<>();

        List<WeightedHeuristic<Heuristic.KeyHeuristic<?>>> heuristics = keyHeuristics.get(key.getClass());
        if (heuristics != null) {
            for (WeightedHeuristic<Heuristic.KeyHeuristic<?>> h : heuristics) {
                double s = getScore(key, h);
                scores.put(h.heuristic.getName(), s * h.weight);
            }
        }

        if (key instanceof GroupKey.Composite c) {
            for (GroupKey sub : c.parts()) {
                Map<String, Double> subScores = getKeyScoresRecursively(sub, cache);
                subScores.forEach((k, v) -> scores.merge(k, v, Double::sum));
            }
        }

        cache.put(key, scores);
        return scores;
    }

    @SuppressWarnings("unchecked")
    private double getScore(GroupKey key, WeightedHeuristic<?> h) {
        // Safe cast because we registered it with the correct type
        Heuristic.KeyHeuristic<GroupKey> typedH = (Heuristic.KeyHeuristic<GroupKey>) h.heuristic;
        return typedH.score(key);
    }

    private Map<String, Double> scoreItem(RunResult item) {
        Map<String, Double> scores = new HashMap<>();
        for (var h : itemHeuristics) {
            scores.put(h.heuristic.getName(), h.heuristic.score(item) * h.weight);
        }
        return scores;
    }

    private GroupKey createKey(RunResult r, Pool pool, GroupingStrategy groupingStrategy) {
        return switch (groupingStrategy) {
            case GroupingStrategy.NoGroups s -> new GroupKey.Generic("All");
            case GroupingStrategy.CompositeGroups c -> {
                yield new GroupKey.Composite(c.groups());
            }
            case GroupingStrategy.CompositeAllGroups c -> {
                var k1 = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                var k2 = GroupKey.PathHash.from(r.getTrace(), pool);
                var k3 = GroupKey.OutputShape.from(r.getTrace());
                var k4 = GroupKey.ExceptionType.from(r.getTrace());
                yield new GroupKey.Composite(List.of(k1, k2, k3, k4));
            }
        };
    }
}
