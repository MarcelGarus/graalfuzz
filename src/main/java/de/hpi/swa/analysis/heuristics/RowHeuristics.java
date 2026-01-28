package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.operations.Materializer;
import de.hpi.swa.analysis.query.ColumnDef.PreparableRowColumn;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Universe;
import de.hpi.swa.generator.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Row-level heuristics that compute scores for individual RunResults.
 * These are cached per row during materialization.
 */
public class RowHeuristics {
    public static final MinimalInput MINIMAL_INPUT = new MinimalInput();
    public static final MinimalOutput MINIMAL_OUTPUT = new MinimalOutput();

    /**
     * Minimal inputs are simpler and easier to understand.
     * Score is normalized so smaller input = higher score.
     */
    public static class MinimalInput extends PreparableRowColumn<Double> {

        private static final ColumnId<Double> ID = ColumnId.of("MinimalInput");

        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        public MinimalInput() {
            super(ID);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            List<Double> scores = new ArrayList<>();
            for (RunResult result : results) {
                scores.add(scoreUnnormalized(result));
            }
            normalizer.prepare(scores);
        }

        @Override
        public Double compute(RunResult row) {
            return normalizer.normalize(scoreUnnormalized(row));
        }

        private double scoreUnnormalized(RunResult item) {
            return -calculateSize(item.input(), item.universe(), new HashSet<>());
        }

        private double calculateSize(Value val, Universe universe, Set<Integer> visited) {
            if (val == null)
                return 0.0;

            return switch (val) {
                case Value.Null n -> 0.0;
                case Value.Boolean b -> 0.0;
                case Value.Int num -> {
                    int absValue = Math.max(Math.abs(num.value()), 1);
                    yield Math.log10(absValue);
                }
                case Value.Double num -> {
                    double absValue = Math.max(Math.abs(num.value()), 1.0);
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

    /**
     * Minimal outputs are simpler and easier to understand.
     * Score is normalized so smaller output = higher score.
     */
    public static class MinimalOutput extends PreparableRowColumn<Double> {

        private static final ColumnId<Double> ID = ColumnId.of("MinimalOutput");

        private final Normalizer.NormalizationStrategy normalizer = new Normalizer.MinMaxNormalization();

        public MinimalOutput() {
            super(ID);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            List<Double> scores = new ArrayList<>();
            for (RunResult result : results) {
                scores.add(scoreUnnormalized(result));
            }
            normalizer.prepare(scores);
        }

        @Override
        public Double compute(RunResult row) {
            return normalizer.normalize(scoreUnnormalized(row));
        }

        private double scoreUnnormalized(RunResult item) {
            return switch (item.output()) {
                case Runner.FunctionResult.Normal(var typeName, var value) ->
                    value != null ? -value.length() : 0.0;
                case Runner.FunctionResult.Crash(var msg, var stackTrace) -> 0.0;
            };
        }
    }
}
