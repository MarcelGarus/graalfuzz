package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.AnalysisContext;
import de.hpi.swa.analysis.grouping.GroupKey;

public class KeyHeuristics {

    public static class MockKeyHeuristic implements Heuristic.KeyHeuristic<GroupKey.Generic> {
        @Override
        public double score(GroupKey.Generic key, AnalysisContext context) {
            return 1.0;
        }

        @Override
        public String getName() {
            return "MockKeyHeuristic";
        }
    }
}
