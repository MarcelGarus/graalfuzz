package de.hpi.swa.analysis.query;

public sealed interface GroupLimitSpec {

    public record None() implements GroupLimitSpec {
    }

    public record LeafsOnly(int maxGroups) implements GroupLimitSpec {
        public LeafsOnly {
            if (maxGroups == 0 || maxGroups < -1) {
                throw new IllegalArgumentException("maxGroups must be positive or -1 for unlimited");
            }
        }
    }

    public record All(int maxGroups) implements GroupLimitSpec {
        public All {
            if (maxGroups < -1) {
                throw new IllegalArgumentException("maxGroups must be positive or -1 for unlimited");
            }
        }
    }
}
