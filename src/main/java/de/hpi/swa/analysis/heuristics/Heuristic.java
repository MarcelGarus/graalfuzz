package de.hpi.swa.analysis.heuristics;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.List;
import de.hpi.swa.generator.Pool;

public interface Heuristic {

    interface KeyHeuristic<K extends GroupKey> {
        /**
         * Heuristics on key level like input shape, path hash or output shape.
         * Heuristics aim to maximize scores.
         */
        double score(K key);

        String getName();

        default void prepare(List<RunResult> results, Pool pool) {
        }
    }

    interface ItemHeuristic {
        /**
         * Heuristics on item level like minimal input or minimal output.
         * Heuristics aim to maximize scores.
         */
        double score(RunResult item);

        String getName();

        default void prepare(List<RunResult> results, Pool pool) {
        }
    }
}
