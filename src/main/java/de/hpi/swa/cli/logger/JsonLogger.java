package de.hpi.swa.cli.logger;

import com.google.gson.Gson;

import de.hpi.swa.analysis.AnalysisEngine.MultiQueryResult;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.serialization.GsonConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonLogger implements ResultLogger {
    private final Gson gson = GsonConfig.createGson();

    @Override
    public void logRun(RunResult result) {
        var jsonElement = gson.toJsonTree(result);
        if (jsonElement.isJsonObject()) {
            jsonElement.getAsJsonObject().addProperty("type", "run");
        }
        System.out.println(gson.toJson(jsonElement));
    }

    @Override
    public void logAnalysis(String queryName, ResultGroup<?, ?> rootGroup) {
        var output = Map.of(
                "type", "analysis",
                "query", queryName,
                "groups", convertGroup(rootGroup));

        System.out.println(gson.toJson(output));
    }

    @Override
    public void logMultipleAnalyses(MultiQueryResult multiResult) {
        for (String queryName : multiResult.queryNames()) {
            var output = Map.of(
                    "type", "analysis",
                    "query", queryName,
                    "groups", convertGroup(multiResult.get(queryName)));

            System.out.println(gson.toJson(output));
        }
    }

    private Map<String, Object> convertGroup(ResultGroup<?, ?> group) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (group.groupingSpec() != null) {
            result.put("column", formatGroupingSpec(group.groupingSpec()));
            result.put("key", String.valueOf(group.groupKey()));
        }

        result.put("aggregations", group.aggregations());

        if (!group.projectedGroupData().isEmpty()) {
            Map<String, Object> projectedGroup = new LinkedHashMap<>();
            for (var entry : group.projectedGroupData().entrySet()) {
                projectedGroup.put(entry.getKey().name(), entry.getValue());
            }
            result.put("projectedGroupData", projectedGroup);
        }

        if (!group.children().isEmpty()) {
            List<Map<String, Object>> childMaps = new ArrayList<>();
            for (var child : group.children().values()) {
                childMaps.add(convertGroup(child));
            }
            result.put("children", childMaps);
        }

        List<RunResult> results = group.results();
        if (!results.isEmpty()) {
            result.put("sampleCount", results.size());

            List<Map<String, Object>> samples = new ArrayList<>();
            for (RunResult r : results) {
                Map<String, Object> sampleMap = new LinkedHashMap<>();

                sampleMap.put("input", r.getInput());
                sampleMap.put("output", r.getOutput());
                sampleMap.put("trace", r.getTrace());

                Map<ColumnDef<?>, Object> projectedRow = group.projectedRowData().get(r);
                if (projectedRow != null && !projectedRow.isEmpty()) {
                    Map<String, Object> projectedData = new LinkedHashMap<>();
                    for (var entry : projectedRow.entrySet()) {
                        projectedData.put(entry.getKey().name(), entry.getValue());
                    }
                    sampleMap.put("projected", projectedData);
                }

                samples.add(sampleMap);
            }
            result.put("samples", samples);
        }

        return result;
    }

    private String formatGroupingSpec(GroupingSpec spec) {
        return switch (spec) {
            case GroupingSpec.Single<?> s -> s.column().name();
            case GroupingSpec.Composite c -> c.columns().stream()
                    .map(col -> col.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        };
    }
}
