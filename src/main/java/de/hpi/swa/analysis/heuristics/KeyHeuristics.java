package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.operations.Materializer;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.Shape;
import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.analysis.query.ColumnDef.PreparableKeyColumn;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Key-level heuristics that compute scores based on key values
 * (e.g. InputShape).
 * These are cached per unique key value, reducing computation.
 */
public class KeyHeuristics {

    /**
     * Simpler input shapes are easier to display and understand.
     * Score is normalized and inverted so simpler = higher score.
     */
    public static class InputShapeSimplicity extends PreparableKeyColumn<Shape, Double> {

        public static final ColumnId<Double> ID = ColumnId.of("InputShapeSimplicity");

        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        public InputShapeSimplicity() {
            super(ID, ColumnDef.INPUT_SHAPE);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            List<Double> scores = new ArrayList<>();
            for (RunResult r : results) {
                Shape shape = materializer.materialize(r, ColumnDef.INPUT_SHAPE);
                scores.add(scoreUnnormalized(shape));
            }
            normalizer.prepare(scores);
        }

        @Override
        public Double compute(Shape key) {
            return normalizer.normalize(scoreUnnormalized(key));
        }

        private double scoreUnnormalized(Shape key) {
            return -calculateComplexity(key);
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

    /**
     * Inputs where output shapes are unevenly distributed indicate edge cases.
     * High diversity score = more interesting input shape.
     */
    public static class OutputShapeDiversity extends PreparableKeyColumn<Shape, Double> {

        public static final ColumnId<Double> ID = ColumnId.of("OutputShapeDiversity");

        private final Map<Shape, Double> variances = new HashMap<>();

        public OutputShapeDiversity() {
            super(ID, ColumnDef.INPUT_SHAPE);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            Map<Shape, Map<String, Integer>> inputOutputCounts = new HashMap<>();
            Map<Shape, Integer> inputCounts = new HashMap<>();

            for (RunResult r : results) {
                Shape inputShape = materializer.materialize(r, ColumnDef.INPUT_SHAPE);
                String outputType = materializer.materialize(r, ColumnDef.OUTPUT_TYPE);

                inputOutputCounts.computeIfAbsent(inputShape, k -> new HashMap<>()).merge(outputType, 1, Integer::sum);
                inputCounts.merge(inputShape, 1, Integer::sum);
            }

            for (Shape inputShape : inputOutputCounts.keySet()) {
                variances.put(inputShape, calculateOutputDiversity(
                        inputOutputCounts.getOrDefault(inputShape, Map.of()).values(),
                        inputCounts.getOrDefault(inputShape, 0)));
            }
        }

        @Override
        public Double compute(Shape key) {
            // Variance between 0 and 0.25, normalize to 0-1
            return Math.min(variances.getOrDefault(key, 0.0) * 4.0, 1.0);
        }

        private double calculateOutputDiversity(java.util.Collection<Integer> N_i, Integer N) {
            if (N == 0 || N_i.isEmpty())
                return 0.0;

            int inputShapeCount = N_i.size();
            List<Double> p_i = N_i.stream()
                    .map(count -> (double) count / N)
                    .toList();

            double mean = p_i.stream().reduce(0.0, Double::sum) / inputShapeCount;

            double variance = 0.0;
            for (double p : p_i) {
                variance += Math.pow(p - mean, 2);
            }

            return variance / inputShapeCount;
        }
    }

    /**
     * Inputs where all outputs fail should be penalized. But inputs where many
     * outputs fail,
     * but some succeed are interesting edge cases and should be scored highly.
     */
    public static class InputValidity extends PreparableKeyColumn<Shape, Double> {

        public static final ColumnId<Double> ID = ColumnId.of("InputValidity");

        private final Map<Shape, Double> scores = new HashMap<>();

        public InputValidity() {
            super(ID, ColumnDef.INPUT_SHAPE);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            Map<Shape, Map<String, Integer>> inputOutputCounts = new HashMap<>();

            for (RunResult r : results) {
                Shape inputShape = materializer.materialize(r, ColumnDef.INPUT_SHAPE);
                String outputType = materializer.materialize(r, ColumnDef.OUTPUT_TYPE);

                inputOutputCounts.computeIfAbsent(inputShape, k -> new HashMap<>()).merge(outputType, 1, Integer::sum);
            }

            for (Shape inputShape : inputOutputCounts.keySet()) {
                scores.put(inputShape, calculateScore(inputOutputCounts.get(inputShape)));
            }
        }

        @Override
        public Double compute(Shape key) {
            return scores.getOrDefault(key, 0.5);
        }

        private double calculateScore(Map<String, Integer> outputCounts) {
            int crashCount = 0;
            int totalCount = 0;
            for (Map.Entry<String, Integer> entry : outputCounts.entrySet()) {
                int count = entry.getValue();
                if (entry.getKey().equals("Crash")) {
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

    /**
     * Short paths are interesting because they indicate edge cases.
     * Score is normalized and inverted so shorter = higher score.
     */
    public static class PathSimplicity extends PreparableKeyColumn<Coverage, Double> {

        public static final ColumnId<Double> ID = ColumnId.of("PathSimplicity");

        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        public PathSimplicity() {
            super(ID, ColumnDef.COVERAGE_PATH);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            List<Double> pathLengths = new ArrayList<>();
            for (RunResult r : results) {
                Coverage coverage = materializer.materialize(r, ColumnDef.COVERAGE_PATH);
                pathLengths.add(calculateSimplicity(coverage));
            }
            normalizer.prepare(pathLengths);
        }

        @Override
        public Double compute(Coverage coverage) {
            return normalizer.normalize(calculateSimplicity(coverage));
        }

        private double calculateSimplicity(Coverage coverage) {
            return -coverage.getCovered().size();
        }
    }

    /**
     * Rare paths are interesting because they indicate edge cases.
     * We uniformly debias over input shapes to remove fuzzer biasing certain
     * shapes.
     */
    public static class CoverageRarity extends PreparableKeyColumn<Coverage, Double> {

        public static final ColumnId<Double> ID = ColumnId.of("CoverageRarity");

        private final Map<Shape, Integer> inputCounts = new HashMap<>();
        private final Map<Coverage, Map<Shape, Integer>> pathInputCounts = new HashMap<>();

        public CoverageRarity() {
            super(ID, ColumnDef.COVERAGE_PATH);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            inputCounts.clear();
            pathInputCounts.clear();

            for (RunResult r : results) {
                Shape inputShape = materializer.materialize(r, ColumnDef.INPUT_SHAPE);
                Coverage coverage = materializer.materialize(r, ColumnDef.COVERAGE_PATH);

                inputCounts.merge(inputShape, 1, Integer::sum);
                pathInputCounts.computeIfAbsent(coverage, k -> new HashMap<>()).merge(inputShape, 1, Integer::sum);
            }
        }

        @Override
        public Double compute(Coverage coverage) {
            return calculateCoverageRarity(coverage);
        }

        private double calculateCoverageRarity(Coverage coverage) {
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

            double sum = 0.0;
            int shapeCount = 0;

            var inputCountsOfCurrentPath = pathInputCounts.getOrDefault(coverage, Map.of());
            for (Shape inputShape : inputCounts.keySet()) {
                int inputCount = inputCounts.getOrDefault(inputShape, 0);
                if (inputCount == 0)
                    continue;

                int inputPathCount = inputCountsOfCurrentPath.getOrDefault(inputShape, 0);

                sum += (double) inputPathCount / inputCount;
                shapeCount++;
            }

            if (shapeCount == 0)
                return 1.0;

            double unbiasedPathHitRate = sum / shapeCount;
            return 1 - unbiasedPathHitRate;
        }
    }
}
