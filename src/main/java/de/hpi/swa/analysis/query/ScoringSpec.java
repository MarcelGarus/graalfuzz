package de.hpi.swa.analysis.query;

import java.util.LinkedHashMap;
import java.util.Map;

import de.hpi.swa.analysis.heuristics.RowHeuristics;
import de.hpi.swa.analysis.heuristics.KeyHeuristics;

public record ScoringSpec(
        Map<ColumnDef<?>, Double> heuristicWeights) {
    public static final String ROW_SCORE_COLUMN = "RowScore";
    public static final String GROUP_SCORE_COLUMN = "GroupScore";

    public ScoringSpec {
        for (var entry : heuristicWeights.entrySet()) {
            if (entry.getKey() instanceof ColumnDef.AggregationRef) {
                throw new IllegalArgumentException("AggregationRef cannot be used in ScoringSpec");
            }

            if (entry.getValue() < 0.0) {
                throw new IllegalArgumentException("Heuristic weights must be non-negative");
            }
        }

        heuristicWeights = Map.copyOf(heuristicWeights);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ScoringSpec defaultConfig() {
        return builder()
                .weight(RowHeuristics.MINIMAL_INPUT, 5.0)
                .weight(RowHeuristics.MINIMAL_OUTPUT, 1.0)
                .weight(new KeyHeuristics.InputValidity(), 100.0)
                .weight(new KeyHeuristics.InputShapeSimplicity(), 10.0)
                .weight(new KeyHeuristics.OutputShapeDiversity(), 1.0)
                .weight(new KeyHeuristics.CoverageRarity(), 2.0)
                .weight(new KeyHeuristics.PathSimplicity(), 1.0)
                .build();
    }

    public double totalWeight() {
        return heuristicWeights.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public Map<ColumnDef<?>, Double> keyHeuristicWeights() {
        Map<ColumnDef<?>, Double> keyWeights = new LinkedHashMap<>();
        for (var entry : heuristicWeights.entrySet()) {
            if (entry.getKey() instanceof ColumnDef.PreparableKeyColumn<?, ?>) {
                keyWeights.put(entry.getKey(), entry.getValue());
            }
        }
        return keyWeights;
    }

    public Map<ColumnDef<?>, Double> rowHeuristicWeights() {
        Map<ColumnDef<?>, Double> rowWeights = new LinkedHashMap<>();
        for (var entry : heuristicWeights.entrySet()) {
            if (entry.getKey() instanceof ColumnDef.PreparableRowColumn<?>) {
                rowWeights.put(entry.getKey(), entry.getValue());
            }
        }
        return rowWeights;
    }

    public ColumnDef<Double> createRowScoreColumn() {
        Map<ColumnDef<?>, Double> weights = heuristicWeights;
        double totalWeight = totalWeight();

        return new ColumnDef.RowComputed<>(
                ColumnDef.ColumnId.of(ROW_SCORE_COLUMN),
                ctx -> {
                    double score = 0.0;
                    for (var entry : weights.entrySet()) {
                        ColumnDef<?> heuristic = entry.getKey();
                        double weight = entry.getValue();
                        Object value = ctx.get(heuristic);
                        if (value instanceof Number num) {
                            score += num.doubleValue() * weight;
                        }
                    }
                    return totalWeight > 0 ? score / totalWeight : 0.0;
                });
    }

    public static final class Builder {
        private final Map<ColumnDef<?>, Double> weights = new LinkedHashMap<>();

        public Builder weight(ColumnDef<?> heuristic, double weight) {
            weights.put(heuristic, weight);
            return this;
        }

        public ScoringSpec build() {
            return new ScoringSpec(weights);
        }
    }
}
