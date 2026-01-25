package de.hpi.swa.analysis.query;

public sealed interface DrillSpec {

    public record None() implements DrillSpec {}

    public record LeafsOnly(int topN) implements DrillSpec {
        public LeafsOnly() {
            this(3);
        }
    }

    public record All(int topN) implements DrillSpec {
        public All() {
            this(3);
        }
    }
}