package de.hpi.swa.cli.logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.hpi.swa.analysis.AnalysisEngine.MultiQueryResult;
import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.analysis.query.Shape;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.serialization.GsonConfig;

import java.util.ArrayList;
import java.util.Collection;
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
                "root", convertGroup(rootGroup));

        System.out.println(gson.toJson(output));
    }

    @Override
    public void logMultipleAnalyses(MultiQueryResult multiResult) {
        for (String queryName : multiResult.queryNames()) {
            var output = Map.of(
                    "type", "analysis",
                    "query", queryName,
                    "root", convertGroup(multiResult.get(queryName)));

            System.out.println(gson.toJson(output));
        }
    }

    private Map<String, Object> convertGroup(ResultGroup<?, ?> group) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (group.groupingSpec() != null) {
            result.put("column", formatGroupingSpec(group.groupingSpec()));
            result.put("key", gson.toJsonTree(group.groupKey(), GroupKey.class));
        }

        // Serialize aggregations with proper type handling for Shapes in collections
        result.put("aggregations", serializeAggregations(group.aggregations()));

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

            List<JsonObject> samples = new ArrayList<>();
            for (RunResult r : results) {
                var jsonElement = gson.toJsonTree(r);

                Map<ColumnDef<?>, Object> projectedRow = group.projectedRowData().get(r);
                if (projectedRow != null && !projectedRow.isEmpty()) {
                    Map<String, Object> projectedData = new LinkedHashMap<>();
                    for (var entry : projectedRow.entrySet()) {
                        projectedData.put(entry.getKey().name(), entry.getValue());
                    }
                    jsonElement.getAsJsonObject().add("projected", gson.toJsonTree(projectedData));
                }

                samples.add(jsonElement.getAsJsonObject());
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
            case GroupingSpec.Hierarchical h -> h.columns().stream()
                    .map(col -> col.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        };
    }

    /**
     * Serializes aggregations map with proper type handling for Shapes in
     * collections.
     * This is needed because Gson doesn't use custom adapters for elements within
     * untyped collections.
     */
    private JsonElement serializeAggregations(Map<String, Object> aggregations) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, Object> entry : aggregations.entrySet()) {
            result.add(entry.getKey(), serializeAggregationValue(entry.getValue()));
        }
        return result;
    }

    private JsonElement serializeAggregationValue(Object value) {
        if (value instanceof Shape shape) {
            return gson.toJsonTree(shape, Shape.class);
        } else if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            for (Object element : collection) {
                array.add(serializeAggregationValue(element));
            }
            return array;
        } else if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                obj.add(String.valueOf(entry.getKey()), serializeAggregationValue(entry.getValue()));
            }
            return obj;
        } else {
            return gson.toJsonTree(value);
        }
    }
}
