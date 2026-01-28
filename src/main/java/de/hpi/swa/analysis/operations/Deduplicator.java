package de.hpi.swa.analysis.operations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.generator.Runner.RunResult;

public class Deduplicator {
    public static List<RunResult> deduplicateResults(List<RunResult> results, List<ColumnDef<?>> deduplicateBy,
            Materializer materializer) {
        if (deduplicateBy.isEmpty()) {
            return results;
        }

        Set<List<Object>> seenKeys = new LinkedHashSet<>();
        List<RunResult> deduplicated = new ArrayList<>();

        for (RunResult result : results) {
            List<Object> key = new ArrayList<>();
            for (ColumnDef<?> col : deduplicateBy) {
                key.add(materializer.materialize(result, col));
            }

            if (seenKeys.add(key)) {
                deduplicated.add(result);
            }
        }

        return deduplicated;
    }
}
