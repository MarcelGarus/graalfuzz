package de.hpi.swa.cli.logger;

import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.generator.Value;
import java.util.List;
import java.util.stream.Collectors;

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
    public void logAnalysis(String queryName, ResultGroup<?, ?> rootGroup) {
        System.out.println("\n--- Analysis Summary: " + queryName + " ---");
        if (!rootGroup.aggregations().isEmpty()) {
            System.out.println("  Total: " + rootGroup.aggregations());
        }
        printGroup(rootGroup, 0);
    }

    private void printGroup(ResultGroup<?, ?> group, int indentLevel) {
        String indent = "  ".repeat(indentLevel);

        if (group.groupingSpec() != null) {
            String specName = formatGroupingSpec(group.groupingSpec());
            String keyStr = formatGroupKey(group.groupKey());
            System.out.println(String.format("%sGroup %s = %s", indent, specName, keyStr));

            if (!group.aggregations().isEmpty()) {
                System.out.println(String.format("%s  Aggs: %s", indent, group.aggregations()));
            }
        }

        if (!group.children().isEmpty()) {
            for (var child : group.children().values()) {
                printGroup(child, indentLevel + 1);
            }
        } else {
            List<RunResult> results = group.results();
            int count = Math.min(3, results.size());
            for (int i = 0; i < count; i++) {
                var result = results.get(i);
                System.out.print(String.format("%s  %-20s", indent + "  ",
                        Value.format(result.getInput(), result.getUniverse())));
                System.out.print("  Trace: " + result.getTrace().toString(color));
                System.out.println();
            }
        }

        if (group.groupingSpec() != null) {
            System.out.println();
        }
    }

    private String formatGroupingSpec(GroupingSpec spec) {
        return switch (spec) {
            case GroupingSpec.Single<?> s -> s.column().name();
            case GroupingSpec.Composite c -> c.columns().stream()
                    .map(col -> col.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            case GroupingSpec.Hierarchical h -> h.columns().stream()
                    .map(col -> col.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        };
    }

    /**
     * Format a group key, handling Trace specially to respect color settings.
     * Also handles composite keys where parts may contain Traces.
     */
    private String formatGroupKey(Object key) {
        if (key instanceof Trace trace) {
            return trace.toString(color);
        }
        if (key instanceof GroupKey.Composite composite) {
            return composite.parts().stream()
                    .map(part -> part.column().name() + "=" + formatValue(part.value()))
                    .collect(Collectors.joining(" | "));
        }
        if (key instanceof GroupKey.Single<?> single) {
            return single.column().name() + "=" + formatValue(single.value());
        }
        return String.valueOf(key);
    }

    private String formatValue(Object value) {
        if (value instanceof Trace trace) {
            return trace.toString(color);
        }
        return String.valueOf(value);
    }
}
