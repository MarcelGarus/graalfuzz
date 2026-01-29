package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisResult(
        String groupColumn,
        Object groupKey,
        Map<String, Object> aggregations,
        Map<ColumnDef<?>, Object> projectedGroupData,
        List<RowResult> samples,
        int sampleCount,
        List<AnalysisResult> children
) {
    public record RowResult(
            RunResult original,
            Map<ColumnDef<?>, Object> projectedData
    ) {
        public RowResult {
            projectedData = projectedData == null ? Map.of() : Map.copyOf(projectedData);
        }

        public static RowResult from(RunResult row, Map<ColumnDef<?>, Object> projected) {
            return new RowResult(row, projected);
        }
    }

    public AnalysisResult {
        aggregations = aggregations == null ? Map.of() : Map.copyOf(aggregations);
        projectedGroupData = projectedGroupData == null ? Map.of() : Map.copyOf(projectedGroupData);
        samples = samples == null ? List.of() : List.copyOf(samples);
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static AnalysisResult fromResultGroup(ResultGroup<?, ?> group) {
        return fromResultGroupRecursive(group);
    }

    private static AnalysisResult fromResultGroupRecursive(ResultGroup<?, ?> group) {
        String columnName = null;
        if (group.groupingSpec() != null) {
            columnName = switch (group.groupingSpec()) {
                case de.hpi.swa.analysis.query.GroupingSpec.Single<?> s -> s.column().name();
                case de.hpi.swa.analysis.query.GroupingSpec.Composite c ->
                    c.columns().stream()
                        .map(col -> col.name())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                case de.hpi.swa.analysis.query.GroupingSpec.Hierarchical h ->
                    h.columns().stream()
                            .map(col -> col.name())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
            };
        }

        List<RowResult> rowResults = group.results().stream()
                .map(row -> {
                    Map<ColumnDef<?>, Object> projected = group.projectedRowData().get(row);
                    return RowResult.from(row, projected != null ? projected : Map.of());
                })
                .toList();

        List<AnalysisResult> childResults = group.children().values().stream()
                .map(AnalysisResult::fromResultGroupRecursive)
                .toList();

        return new AnalysisResult(
                columnName,
                group.groupKey(),
                new LinkedHashMap<>(group.aggregations()),
                new LinkedHashMap<>(group.projectedGroupData()),
                rowResults,
                rowResults.size(),
                childResults
        );
    }
}
