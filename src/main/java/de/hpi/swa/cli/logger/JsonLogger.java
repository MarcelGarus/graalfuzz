package de.hpi.swa.cli.logger;

import com.google.gson.Gson;
import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.serialization.GsonConfig;
import java.util.List;
import java.util.Map;

public class JsonLogger implements ResultLogger {
    private final Gson gson = GsonConfig.createGson();

    @Override
    public void logRun(RunResult result) {
        // Complicated way to add a "type" field to the serialized JSON
        var jsonElement = gson.toJsonTree(result);
        if (jsonElement.isJsonObject()) {
            jsonElement.getAsJsonObject().addProperty("type", "run");
        }
        System.out.println(gson.toJson(jsonElement));
    }

    @Override
    public void logAnalysis(List<ResultGroup> groupList) {
        // Create a structured map for JSON output
        var groupsWithTopSamples = Map.of(
                "type", "analysis",
                "groups", groupList.stream()
                        .map(g -> g.top(3))
                        .toList());

        // Print the structured map as JSON
        System.out.println(gson.toJson(groupsWithTopSamples));
    }
}
