package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.generator.Runner.RunResult;

public interface Heuristic {

    interface KeyHeuristic<K extends GroupKey> {
        double score(K key);

        String getName();
    }

    interface ItemHeuristic {
        double score(RunResult item);

        String getName();
    }
}
