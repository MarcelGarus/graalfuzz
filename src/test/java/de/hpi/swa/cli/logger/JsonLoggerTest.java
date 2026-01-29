package de.hpi.swa.cli.logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.hpi.swa.analysis.AnalysisEngine;
import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.analysis.query.NamedQuery;
import de.hpi.swa.analysis.query.Query;
import de.hpi.swa.analysis.query.Shape;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.FunctionResult;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Universe;
import de.hpi.swa.generator.Value;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonLoggerTest {

    private JsonLogger logger;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @Before
    public void setUp() {
        logger = new JsonLogger();
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    private String getCapturedOutput() {
        return outputStream.toString().trim();
    }

    private RunResult createMockRunResult(String inputType, String outputType, boolean crashed) {
        Universe universe = new Universe();
        Value input = createMockValue(inputType, universe);
        FunctionResult output = crashed
                ? new FunctionResult.Crash("TestError: Something went wrong", List.of("at line 1", "at line 2"))
                : new FunctionResult.Normal(outputType, "testValue");
        Trace trace = new Trace();
        return new RunResult(universe, input, output, trace);
    }

    private Value createMockValue(String type, Universe universe) {
        return switch (type) {
            case "int" -> new Value.Int(42);
            case "double" -> new Value.Double(3.14);
            case "string" -> new Value.StringValue("test");
            case "boolean" -> new Value.Boolean(true);
            case "object" -> new Value.ObjectValue(universe.createObject());
            default -> new Value.Null();
        };
    }

    @Test
    public void testLogRunWithNormalResult() {
        RunResult result = createMockRunResult("int", "string", false);
        
        logger.logRun(result);
        
        String output = getCapturedOutput();
        assertFalse(output.isEmpty());
        
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        assertEquals("run", json.get("type").getAsString());
        assertTrue(json.has("universe"));
        assertTrue(json.has("input"));
        assertTrue(json.has("didCrash"));
        assertEquals(false, json.get("didCrash").getAsBoolean());
        assertEquals("Normal", json.get("outputType").getAsString());
        assertEquals("string", json.get("typeName").getAsString());
        assertEquals("testValue", json.get("value").getAsString());
    }

    @Test
    public void testLogRunWithCrashResult() {
        RunResult result = createMockRunResult("int", "", true);
        
        logger.logRun(result);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        assertEquals("run", json.get("type").getAsString());
        assertEquals(true, json.get("didCrash").getAsBoolean());
        assertEquals("Crash", json.get("outputType").getAsString());
        assertTrue(json.get("message").getAsString().contains("TestError"));
    }

    @Test
    public void testLogRunWithInputValueSerialization() {
        RunResult result = createMockRunResult("int", "string", false);
        
        logger.logRun(result);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        // Check that the input Value is properly serialized with type field
        JsonObject input = json.getAsJsonObject("input");
        assertTrue(input.has("type"));
        assertEquals("Int", input.get("type").getAsString());
        assertEquals(42, input.get("value").getAsInt());
    }

    @Test
    public void testLogAnalysisWithSimpleGroup() {
        // Create a simple result group
        ResultGroup<GroupKey.Single<String>, GroupKey.Root> rootGroup = ResultGroup.root();
        
        Map<String, Object> aggregations = new LinkedHashMap<>();
        aggregations.put("Count", 5);
        aggregations.put("CrashCount", 1);
        
        ResultGroup<?, GroupKey.Root> groupWithAggregations = new ResultGroup<>(
            null,
            rootGroup.groupKey(),
            null,
            List.of(),
            Map.of(),
            aggregations,
            Map.of(),
            Map.of()
        );
        
        logger.logAnalysis("testQuery", groupWithAggregations);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        assertEquals("analysis", json.get("type").getAsString());
        assertEquals("testQuery", json.get("query").getAsString());
        assertTrue(json.has("root"));
        
        JsonObject root = json.getAsJsonObject("root");
        assertTrue(root.has("aggregations"));
        
        JsonObject aggs = root.getAsJsonObject("aggregations");
        assertEquals(5, aggs.get("Count").getAsInt());
        assertEquals(1, aggs.get("CrashCount").getAsInt());
    }

    @Test
    public void testLogAnalysisWithShapeInAggregations() {
        ResultGroup<GroupKey.Single<String>, GroupKey.Root> rootGroup = ResultGroup.root();
        
        // Create aggregations with Shape values
        Map<String, Object> aggregations = new LinkedHashMap<>();
        Set<Shape> inputShapes = Set.of(new Shape.Int(), new Shape.StringShape());
        aggregations.put("InputShapes", inputShapes);
        
        ResultGroup<?, GroupKey.Root> groupWithShapes = new ResultGroup<>(
            null,
            rootGroup.groupKey(),
            null,
            List.of(),
            Map.of(),
            aggregations,
            Map.of(),
            Map.of()
        );
        
        logger.logAnalysis("testQuery", groupWithShapes);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        JsonObject root = json.getAsJsonObject("root");
        JsonObject aggs = root.getAsJsonObject("aggregations");
        assertTrue(aggs.has("InputShapes"));
        
        // Verify that shapes are properly serialized as objects with type field
        JsonArray shapes = aggs.getAsJsonArray("InputShapes");
        assertTrue(shapes.size() > 0);
        for (int i = 0; i < shapes.size(); i++) {
            JsonObject shape = shapes.get(i).getAsJsonObject();
            assertTrue(shape.has("type"));
        }
    }

    @Test
    public void testLogAnalysisWithGroupKey() {
        ColumnDef<String> column = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputType"), r -> "int");
        GroupKey.Single<String> groupKey = new GroupKey.Single<>(column, "int");
        GroupingSpec spec = new GroupingSpec.Single<>(column);
        
        ResultGroup<?, GroupKey.Single<String>> group = new ResultGroup<>(
            spec,
            groupKey,
            null,
            List.of(),
            Map.of(),
            Map.of("Count", 10),
            Map.of(),
            Map.of()
        );
        
        logger.logAnalysis("testQuery", group);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        JsonObject root = json.getAsJsonObject("root");
        assertTrue(root.has("column"));
        assertTrue(root.has("key"));
        
        assertEquals("OutputType", root.get("column").getAsString());
        
        // Verify key is serialized as object (not string)
        JsonObject key = root.getAsJsonObject("key");
        assertEquals("Single", key.get("type").getAsString());
        assertEquals("OutputType", key.get("column").getAsString());
        assertEquals("int", key.get("value").getAsString());
    }

    @Test
    public void testLogAnalysisWithChildren() {
        ResultGroup<GroupKey.Single<String>, GroupKey.Root> rootGroup = ResultGroup.root();
        
        ColumnDef<String> column = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputType"), r -> "int");
        GroupKey.Single<String> childKey = new GroupKey.Single<>(column, "int");
        
        ResultGroup<?, GroupKey.Single<String>> childGroup = rootGroup.addChildForKey(
            childKey, 
            new GroupingSpec.Single<>(column)
        );
        
        childGroup.aggregations().put("Count", 5);
        
        logger.logAnalysis("testQuery", rootGroup);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        JsonObject root = json.getAsJsonObject("root");
        assertTrue(root.has("children"));
        
        JsonArray children = root.getAsJsonArray("children");
        assertEquals(1, children.size());
        
        JsonObject child = children.get(0).getAsJsonObject();
        assertTrue(child.has("aggregations"));
        assertEquals(5, child.getAsJsonObject("aggregations").get("Count").getAsInt());
    }

    @Test
    public void testLogAnalysisWithSamples() {
        ResultGroup<GroupKey.Single<String>, GroupKey.Root> rootGroup = ResultGroup.root();
        
        RunResult result1 = createMockRunResult("int", "string", false);
        RunResult result2 = createMockRunResult("double", "int", false);
        
        rootGroup.results().add(result1);
        rootGroup.results().add(result2);
        
        logger.logAnalysis("testQuery", rootGroup);
        
        String output = getCapturedOutput();
        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        
        JsonObject root = json.getAsJsonObject("root");
        assertTrue(root.has("sampleCount"));
        assertEquals(2, root.get("sampleCount").getAsInt());
        
        assertTrue(root.has("samples"));
        JsonArray samples = root.getAsJsonArray("samples");
        assertEquals(2, samples.size());
        
        // Verify samples have proper input serialization
        JsonObject sample1 = samples.get(0).getAsJsonObject();
        assertTrue(sample1.has("input"));
        JsonObject input1 = sample1.getAsJsonObject("input");
        assertEquals("Int", input1.get("type").getAsString());
    }

    @Test
    public void testLogMultipleAnalyses() {
        Pool pool = new Pool();
        
        List<RunResult> results = List.of(
            createMockRunResult("int", "string", false),
            createMockRunResult("double", "int", false)
        );
        
        ColumnDef<String> outputTypeCol = new ColumnDef.Base<String>(
            ColumnDef.ColumnId.of("OutputType"), 
            r -> r.didCrash() ? "Crash" : ((FunctionResult.Normal) r.output()).typeName()
        );
        
        NamedQuery query1 = NamedQuery.of("query1", Query.builder()
            .groupBy(new GroupingSpec.Single<>(outputTypeCol)));
        
        NamedQuery query2 = NamedQuery.of("query2", Query.builder());
        
        AnalysisEngine.MultiQueryResult multiResult = AnalysisEngine.analyzeMultiple(
            List.of(query1, query2), 
            results, 
            pool
        );
        
        logger.logMultipleAnalyses(multiResult);
        
        String output = getCapturedOutput();
        String[] lines = output.split("\n");
        
        // Should have logged two queries
        assertTrue(lines.length >= 2);
        
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                assertEquals("analysis", json.get("type").getAsString());
                assertTrue(json.has("query"));
            }
        }
    }
}
