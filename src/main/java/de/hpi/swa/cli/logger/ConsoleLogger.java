package de.hpi.swa.cli.logger;

import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Value;
import java.util.List;

public class ConsoleLogger implements ResultLogger {
    private final boolean color;

    public ConsoleLogger(boolean color) {
        this.color = color;
    }

    @Override
    public void logRun(RunResult result) {
        System.out.print("New run. ");
        System.out.print(String.format("%-20s", Value.format(result.getInput(), result.getUniverse())));
        System.out.print("  Trace: " + result.getTrace().toString(color));
        System.out.println();
    }

    @Override
    public void logAnalysis(List<ResultGroup> groups) {
        System.out.println("\n--- Analysis Summary ---");
        for (ResultGroup group : groups) {
            System.out.println(String.format("Group [Score: %.2f]: %s", group.score(), group.key()));

            // Print top 3 representatives
            for (int i = 0; i < Math.min(3, group.results().size()); i++) {
                var scoredResult = group.results().get(i);
                var result = scoredResult.result();
                System.out.print(String.format("  %-20s", Value.format(result.getInput(), result.getUniverse())));
                System.out.print("  Trace: " + result.getTrace().toString(color));
                System.out.println();
            }

            System.out.println();
        }
    }
}
