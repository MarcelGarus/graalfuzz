package de.hpi.swa.cli.logger;

import de.hpi.swa.analysis.AnalysisEngine.MultiQueryResult;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.generator.Runner.RunResult;

public interface ResultLogger {
    void logRun(RunResult result);

    void logAnalysis(String queryName, ResultGroup<?, ?> rootGroup);

    default void logMultipleAnalyses(MultiQueryResult multiResult) {
        for (String queryName : multiResult.queryNames()) {
            logAnalysis(queryName, multiResult.get(queryName));
        }
    }
}
