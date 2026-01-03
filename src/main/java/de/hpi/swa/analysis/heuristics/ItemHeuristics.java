package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.generator.Runner.RunResult;

public class ItemHeuristics {
    public static class MockItemHeuristic implements Heuristic.ItemHeuristic {
        @Override
        public double score(RunResult item) {
            return 1.0;
        }

        @Override
        public String getName() {
            return "MockItemHeuristic";
        }
    }
}
