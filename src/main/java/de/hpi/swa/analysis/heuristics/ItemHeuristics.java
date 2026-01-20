package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemHeuristics {

    public static class MinimalInput implements Heuristic.ItemHeuristic {
        /* Minimal inputs are simpler and easier to understand */
        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        @Override
        public void prepare(java.util.List<RunResult> results, de.hpi.swa.generator.Pool pool) {
            List<Double> scores = new ArrayList<>();
            for (RunResult result : results) {
                scores.add(scoreUnnormalized(result));
            }
            normalizer.prepare(scores);
        }

        @Override
        public double score(RunResult item) {
            return normalizer.normalize(scoreUnnormalized(item));
        }

        private double scoreUnnormalized(RunResult item) {
            // Invert because the smaller the size, the better the score should be
            return -calculateSize(item.getInput(), item.getUniverse(), new HashSet<>());
        }

        @Override
        public String getName() {
            return "Minimal Input";
        }

        private double calculateSize(Value val, de.hpi.swa.generator.Universe universe, Set<Integer> visited) {
            if (val == null) {
                return 0.0;
            }
            return switch (val) {
                case Value.Null n -> 0.0;
                case Value.Boolean b -> 0.0;
                case Value.Int num -> {
                    int absValue = Math.max(Math.abs(num.value()), 1);
                    // Avoid log10(0) which returns -Infinity
                    yield Math.log10(absValue);
                }
                case Value.Double num -> {
                    double absValue = Math.max(Math.abs(num.value()), 1.0);
                    // Avoid log10(0) which returns -Infinity, and handle very small numbers
                    yield Math.log10(absValue);
                }
                case Value.StringValue s -> (double) s.value().length();
                case Value.ObjectValue objVal -> {
                    var obj = universe.get(objVal.id());
                    if (obj == null || !visited.add(objVal.id().value)) {
                        yield 1.0;
                    }
                    double sum = obj.members.entrySet().stream()
                            .mapToDouble(e -> e.getKey().length() + calculateSize(e.getValue(), universe, visited))
                            .sum();
                    visited.remove(objVal.id().value);
                    yield sum;
                }
            };
        }
    }

    public static class MinimalOutput implements Heuristic.ItemHeuristic {
        /* Minimal outputs are simpler and easier to understand. */
        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        @Override
        public void prepare(java.util.List<RunResult> results, de.hpi.swa.generator.Pool pool) {
            List<Double> scores = new ArrayList<>();
            for (RunResult result : results) {
                scores.add(scoreUnnormalized(result));
            }
            normalizer.prepare(scores);
        }

        @Override
        public double score(RunResult item) {
            return normalizer.normalize(scoreUnnormalized(item));
        }

        private double scoreUnnormalized(RunResult item) {
            // Invert because the smaller the size, the better the score should be
            if (item.getOutput() instanceof de.hpi.swa.generator.Runner.FunctionResult.Normal n && n.value() != null) {
                return -n.value().length();
            } else {
                return 0.0;
            }
        }

        @Override
        public String getName() {
            return "Minimal Output";
        }
    }
}
