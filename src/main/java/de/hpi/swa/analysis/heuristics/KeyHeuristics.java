package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.generator.Shape;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyHeuristics {

    public static class InputShapeSimplicity implements Heuristic.KeyHeuristic<GroupKey.InputShape> {
        /* Simpler input shapes are easier to display and understand. */
        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        @Override
        public String getName() {
            return "Input Simplicity";
        }

        @Override
        public void prepare(List<RunResult> results, Pool pool) {
            List<Double> scores = new ArrayList<>();
            for (RunResult r : results) {
                GroupKey.InputShape key = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                scores.add(scoreUnnormalized(key));
            }
            normalizer.prepare(scores);
        }

        @Override
        public double score(GroupKey.InputShape key) {
            return normalizer.normalize(scoreUnnormalized(key));
        }

        private double scoreUnnormalized(GroupKey.InputShape key) {
            return -1.0 * calculateComplexity(key.shape()); // Invert to maximize score for simpler shapes
        }

        private int calculateComplexity(Shape shape) {
            if (shape instanceof Shape.ObjectShape objShape) {
                var keys = objShape.keys();
                var size = 0;
                for (String key : keys) {
                    var memberShape = objShape.at(key);
                    size += 2 * calculateComplexity(memberShape);
                }
                return 1 + size;
            } else {
                return 1;
            }
        }
    }

    public static class OutputShapeDiversity implements Heuristic.KeyHeuristic<GroupKey.InputShape> {
        /*
         * Inputs where output shapes are unevenly distributed indicate existing edge
         * cases.
         */

        private final Map<GroupKey.InputShape, Double> variances = new HashMap<>();

        @Override
        public String getName() {
            return "Output Shape Diversity";
        }

        @Override
        public void prepare(List<RunResult> results, Pool pool) {
            Map<GroupKey.InputShape, Map<GroupKey.OutputShape, Integer>> inputOutputCounts = new HashMap<>();
            Map<GroupKey.InputShape, Integer> inputCounts = new HashMap<>();

            for (RunResult r : results) {
                GroupKey.InputShape is = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                GroupKey.OutputShape os = GroupKey.OutputShape.from(r.getTrace());

                inputOutputCounts.computeIfAbsent(is, k -> new HashMap<>()).merge(os, 1, Integer::sum);
                inputCounts.merge(is, 1, Integer::sum);
            }

            for (GroupKey.InputShape inputShape : inputOutputCounts.keySet()) {
                variances.put(inputShape, calculateOutputDiversity(
                        inputOutputCounts.getOrDefault(inputShape, Map.of()).values(),
                        inputCounts.getOrDefault(inputShape, 0)));
            }
        }

        @Override
        public double score(GroupKey.InputShape key) {
            if (variances.containsKey(key)) {
                // numbers between 0 and 1 can have a maximum variance of 0.25
                double normalizationFactor = 4;
                return variances.get(key) * normalizationFactor;
            } else {
                return 0.5;
            }
        }

        public void print() {
            for (var entry : variances.entrySet()) {
                System.out.println("InputShapeDiversity - InputShape: " + entry.getKey() + " Variance: " + entry.getValue());
            }
        }

        private double calculateOutputDiversity(Collection<Integer> N_i, Integer N) {
            if (N == 0 || N_i.isEmpty())
                return 0.0;

            int inputShapeCount = N_i.size();
            List<Double> p_i = N_i.stream()
                    .map(count -> (double) count / N)
                    .toList();

            double mean = p_i.stream().reduce(0.0, Double::sum) / inputShapeCount;

            double variance = 0.0;
            for (double p : p_i) {
                variance += (p - mean) * (p - mean);
            }

            return variance / inputShapeCount;
        }
    }

    public static class InputValidity implements Heuristic.KeyHeuristic<GroupKey.InputShape> {
        /*
         * Inputs where all outputs fail should be penalized. But inputs where many
         * outputs fail,
         * but some succeed are interesting edge cases and should be scored highly.
         */

        private final Map<GroupKey.InputShape, Double> scores = new HashMap<>();

        @Override
        public String getName() {
            return "Input Validity";
        }

        @Override
        public void prepare(List<RunResult> results, Pool pool) {
            Map<GroupKey.InputShape, Map<GroupKey.OutputShape, Integer>> inputOutputCounts = new HashMap<>();
            Map<GroupKey.InputShape, Integer> inputCounts = new HashMap<>();

            for (RunResult r : results) {
                GroupKey.InputShape is = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                GroupKey.OutputShape os = GroupKey.OutputShape.from(r.getTrace());

                inputOutputCounts.computeIfAbsent(is, k -> new HashMap<>()).merge(os, 1, Integer::sum);
                inputCounts.merge(is, 1, Integer::sum);
            }

            for (GroupKey.InputShape inputShape : inputOutputCounts.keySet()) {
                scores.put(inputShape, calculateScore(inputOutputCounts.get(inputShape)));
            }
        }

        @Override
        public double score(GroupKey.InputShape key) {
            return scores.getOrDefault(key, 0.5);
        }

        public void print() {
            for (var entry : scores.entrySet()) {
                System.out.println("InputValidity - InputShape: " + entry.getKey() + " Score: " + entry.getValue());
            }
        }

        private double calculateScore(Map<GroupKey.OutputShape, Integer> outputCounts) {
            int crashCount = 0;
            int totalCount = 0;
            for (GroupKey.OutputShape os : outputCounts.keySet()) {
                int count = outputCounts.get(os);
                if (os.value().equals("crash")) {
                    crashCount += count;
                }
                totalCount += count;
            }
            if (crashCount == 0) {
                return 0.5;
            } else if (crashCount == totalCount) {
                return -1.0;
            } else {
                return 1.0;
            }
        }
    }

    public static class PathSimplicity implements Heuristic.KeyHeuristic<GroupKey.PathHash> {
        /* Short paths are interesting because they indicate edge cases */

        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        @Override
        public String getName() {
            return "Path Simplicity";
        }

        @Override
        public void prepare(List<RunResult> results, Pool pool) {
            List<Double> pathLengths = new ArrayList<>();
            for (RunResult r : results) {
                GroupKey.PathHash ph = GroupKey.PathHash.from(r.getTrace(), pool);
                pathLengths.add(calculateSimplicity(ph));
            }
            normalizer.prepare(pathLengths);
        }

        @Override
        public double score(GroupKey.PathHash key) {
            return normalizer.normalize(calculateSimplicity(key));
        }

        private double calculateSimplicity(GroupKey.PathHash key) {
            return -key.length(); // Invert to maximize score for shorter paths
        }
    }

    public static class CoverageRarity implements Heuristic.KeyHeuristic<GroupKey.PathHash> {
        /*
         * Rare paths are interesting because they indicate edge cases.
         * We uniformly debias over input shapes to remove fuzzer bias.
         */

        private final Map<GroupKey.InputShape, Integer> inputCounts = new HashMap<>();
        private final Map<GroupKey.PathHash, Map<GroupKey.InputShape, Integer>> pathInputCounts = new HashMap<>();

        @Override
        public String getName() {
            return "Coverage Rarity";
        }

        @Override
        public void prepare(List<RunResult> results, Pool pool) {
            inputCounts.clear();
            pathInputCounts.clear();

            for (RunResult r : results) {
                GroupKey.InputShape is = GroupKey.InputShape.from(r.getInput(), r.getUniverse());
                GroupKey.PathHash ph = GroupKey.PathHash.from(r.getTrace(), pool);

                inputCounts.merge(is, 1, Integer::sum);

                pathInputCounts.computeIfAbsent(ph, k -> new HashMap<>()).merge(is, 1, Integer::sum);
            }         
        }

        @Override
        public double score(GroupKey.PathHash key) {
            // It's already a probability between 0 and 1, so no normalization needed
            return calculateCoverageRarity(key);
        }

        public void print() {
            for (var entry : pathInputCounts.entrySet()) {
                System.out.println("CoverageRarity - PathHash: " + entry.getKey() + " InputCounts: ");
                for (var innerEntry : entry.getValue().entrySet()) {
                    System.out.println("    " + innerEntry.getKey() + ": " + innerEntry.getValue());
                }
            }
        }

        private double calculateCoverageRarity(GroupKey.PathHash path) {
            /*
             * Paths that are hit rarely across all input shapes get higher scores.
             * We debias over input shapes to account for fuzzer biasing certain shapes.
             * 
             * Estimate P(path) as Σ_s P(path|input shape=s) * P(input shape=s)
             * with uniform P(input shape=s) = 1/|S|
             * and P(path|input shape=s) = N(path & input shape=s) / N(input shape=s)
             * 
             * Returns 1 - P(path) as likelihood to not hit the path, which corresponds to
             * rarity.
             */

            double sum = 0.0; // Σ_s P(path|input shape=s)
            int shapeCount = 0; // |S|

            var inputCountsOfCurrentPath = pathInputCounts.getOrDefault(path, Map.of());
            for (GroupKey.InputShape is : inputCounts.keySet()) {
                int inputCount = inputCounts.getOrDefault(is, 0);
                if (inputCount == 0)
                    continue;

                int inputPathCount = inputCountsOfCurrentPath.getOrDefault(is, 0);

                sum += (double) inputPathCount / inputCount; // P(path|input shape=s)
                shapeCount++;
            }

            if (shapeCount == 0)
                return 1.0;

            double unbiasedPathHitRate = sum / shapeCount; // P(path)
            return 1 - unbiasedPathHitRate; // P(not path)
        }
    }
}
