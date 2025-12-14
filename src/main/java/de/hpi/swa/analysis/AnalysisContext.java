package de.hpi.swa.analysis;

import de.hpi.swa.analysis.grouping.GroupKey;
import de.hpi.swa.generator.Runner.RunResult;
import java.util.*;

public class AnalysisContext {

    public AnalysisContext(List<RunResult> results, List<GroupKey> keys) {
        // Currently empty. Will be filled with stats computed globally on all results
        // that the heuristics can use.
        // e.g. rarity of certain keys, average path length, etc.
    }
}
