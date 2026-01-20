package de.hpi.swa.cli.logger;

import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.generator.Runner.RunResult;
import java.util.List;

public interface ResultLogger {
    void logRun(RunResult result);

    void logAnalysis(List<ResultGroup> groups);
}
