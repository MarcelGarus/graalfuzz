package de.hpi.swa.analysis.query;

public sealed interface DrillSpec {

    public record None() implements DrillSpec {}

    public record LeafsOnly(int topN) implements DrillSpec {
        public LeafsOnly {
            if (topN == 0 || topN < -1) {
                throw new IllegalArgumentException("topN must be positive or -1 for unlimited");
            }
        }
    }

    public record All(int topN) implements DrillSpec {
        public All {
            if (topN < -1) {
                throw new IllegalArgumentException("topN must be positive or -1 for unlimited");
            }
        }
    }
}