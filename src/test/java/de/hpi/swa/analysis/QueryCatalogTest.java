package de.hpi.swa.analysis;

import de.hpi.swa.analysis.query.*;
import de.hpi.swa.cli.QueryCatalog;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.FunctionResult;
import de.hpi.swa.generator.Runner.RunResult;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Universe;
import de.hpi.swa.generator.Value;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for the QueryCatalog queries to verify they return correct results.
 */
public class QueryCatalogTest {

    private static int uniqueIntCounter = 0;
    private static double uniqueDoubleCounter = 0.0;

    // Helper to create a mock RunResult
    private static RunResult mockResult(String inputType, String outputType, boolean crashed) {
        return mockResult(inputType, outputType, crashed, null);
    }

    private static RunResult mockResult(String inputType, String outputType, boolean crashed, String exceptionType) {
        Universe universe = new Universe();
        Value input = createMockValue(inputType, universe);
        FunctionResult output;
        if (crashed) {
            output = new FunctionResult.Crash(exceptionType != null ? exceptionType : "TestError", List.of());
        } else {
            output = new FunctionResult.Normal(outputType, "testValue");
        }
        Trace trace = new Trace();
        return new RunResult(universe, input, output, trace);
    }

    // Create unique values to avoid deduplication
    private static Value createMockValue(String type, Universe universe) {
        return switch (type) {
            case "int" -> new Value.Int(uniqueIntCounter++);
            case "float" -> new Value.Double(100.0 + uniqueDoubleCounter++);  // Separate range for float
            case "double" -> new Value.Double(uniqueDoubleCounter++);
            case "string" -> new Value.StringValue("test" + uniqueIntCounter++);
            case "bool", "boolean" -> new Value.Boolean(true);
            case "object" -> new Value.ObjectValue(universe.createObject());
            default -> new Value.Null();
        };
    }

    private List<RunResult> createMixedResults() {
        List<RunResult> results = new ArrayList<>();
        // Different input types producing different outputs
        results.add(mockResult("int", "string", false));
        results.add(mockResult("int", "string", false));
        results.add(mockResult("int", "int", false));
        results.add(mockResult("double", "string", false));
        results.add(mockResult("double", "int", false));
        results.add(mockResult("bool", "bool", false));
        results.add(mockResult("bool", "null", false));
        results.add(mockResult("string", "string", false));
        results.add(mockResult("string", "null", false));
        // Some crashes with NON-excluded exceptions (should be included)
        results.add(mockResult("null", "null", true, "TestError"));
        results.add(mockResult("object", "null", true, "AnotherError"));
        return results;
    }

    private List<RunResult> createResultsWithExcludedExceptions() {
        List<RunResult> results = new ArrayList<>();
        // Valid runs
        results.add(mockResult("int", "string", false));
        results.add(mockResult("double", "int", false));
        results.add(mockResult("bool", "bool", false));
        // Crashes with EXCLUDED exceptions (should be filtered out)
        results.add(mockResult("null", "null", true, "TypeError"));
        results.add(mockResult("object", "null", true, "AttributeError"));
        results.add(mockResult("string", "null", true, "KeyError"));
        // Crash with NON-excluded exception (should be included)
        results.add(mockResult("float", "null", true, "ZeroDivisionError"));
        return results;
    }

    @Test
    public void testExcludedExceptionsAreFiltered() {
        List<RunResult> results = createResultsWithExcludedExceptions();
        Query query = QueryCatalog.treeList().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== treeList with excluded exceptions ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            System.out.println("  Group: " + key);
            for (var subEntry : entry.getValue().children().entrySet()) {
                System.out.println("    SubGroup: " + subEntry.getKey());
            }
        }

        // Excluded exceptions (TypeError, AttributeError, KeyError) should be filtered out
        // So we should have groups for: int, double (includes both double and float inputs), bool
        // Plus a Crash group for ZeroDivisionError (non-excluded)
        assertTrue("Should have groups", root.children().size() >= 3);

        // Count how many crash subgroups we have
        int crashGroups = 0;
        for (var entry : root.children().entrySet()) {
            for (var subEntry : entry.getValue().children().entrySet()) {
                if (subEntry.getKey().toString().contains("Crash")) {
                    crashGroups++;
                }
            }
        }
        // Only ZeroDivisionError should produce a crash group (float/double->Crash)
        assertEquals("Should have 1 crash group (ZeroDivisionError not excluded)", 1, crashGroups);
    }

    @Test
    public void testObservedSignature() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.observedSignature().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== observedSignature ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());
        System.out.println("Results: " + root.results().size());

        // Should have aggregations at root level
        assertTrue("Should have InputShapes", root.aggregations().containsKey("InputShapes"));
        assertTrue("Should have InputTypes", root.aggregations().containsKey("InputTypes"));
        assertTrue("Should have OutputTypes", root.aggregations().containsKey("OutputTypes"));
        assertTrue("Should have Count", root.aggregations().containsKey("Count"));

        // Count should be > 1 (we have multiple distinct input values)
        int count = (int) root.aggregations().get("Count");
        System.out.println("Count: " + count);
        assertTrue("Count should be > 1", count > 1);

        // InputTypes should have multiple types
        @SuppressWarnings("unchecked")
        Set<String> inputTypes = (Set<String>) root.aggregations().get("InputTypes");
        System.out.println("InputTypes: " + inputTypes);
        assertTrue("Should have multiple input types", inputTypes.size() > 1);
    }

    @Test
    public void testRelevantPairs() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.relevantPairs().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== relevantPairs ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        // Should have children (groups) for non-crash results
        assertTrue("Should have groups", root.children().size() > 0);

        // Print each group
        int totalResults = 0;
        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            var group = entry.getValue();
            System.out.println("  Group " + key + ": " + group.aggregations());
            System.out.println("    Results: " + group.results().size());
            totalResults += group.results().size();
        }

        System.out.println("Total results in groups: " + totalResults);
        assertTrue("Should have results in groups", totalResults > 0);
    }

    @Test
    public void testTreeList() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.treeList().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== treeList ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        // Should have children (groups by INPUT_SHAPE then OUTPUT_TYPE)
        // After filtering, excluded exceptions (TypeError, AttributeError, KeyError) are removed
        // We have TestError and AnotherError which are NOT excluded, so they remain
        assertTrue("Should have groups", root.children().size() > 0);

        // Print each group
        int totalResults = 0;
        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            var group = entry.getValue();
            System.out.println("  Group " + key + ": " + group.aggregations());
            System.out.println("    Results: " + group.results().size());
            System.out.println("    SubGroups: " + group.children().size());
            totalResults += group.results().size();
            
            for (var subEntry : group.children().entrySet()) {
                var subKey = subEntry.getKey();
                var subGroup = subEntry.getValue();
                System.out.println("      SubGroup " + subKey + ": " + subGroup.aggregations());
                System.out.println("        Results: " + subGroup.results().size());
                totalResults += subGroup.results().size();
            }
        }

        System.out.println("Total results in groups: " + totalResults);
        assertTrue("Should have results in groups", totalResults > 0);
    }

    @Test
    public void testValidExamples() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.validExamples().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== validExamples ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        // Should have children (groups by INPUT_TYPE, TRACE)
        assertTrue("Should have groups", root.children().size() > 0);

        // Print each group
        int totalResults = 0;
        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            var group = entry.getValue();
            System.out.println("  Group " + key + ": " + group.aggregations());
            System.out.println("    Results: " + group.results().size());
            totalResults += group.results().size();
        }

        System.out.println("Total results in groups: " + totalResults);
        assertTrue("Should have results in groups", totalResults > 0);
    }

    @Test
    public void testExceptionExamples() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.exceptionExamples().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== exceptionExamples ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        // Should have children for crash results (filtered to non-excluded exceptions)
        // We added TestError and AnotherError which are NOT in DEFAULT_EXCLUDED_EXCEPTIONS

        // Print each group
        int totalResults = 0;
        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            var group = entry.getValue();
            System.out.println("  Group " + key + ": " + group.aggregations());
            System.out.println("    Results: " + group.results().size());
            totalResults += group.results().size();
        }

        System.out.println("Total results in groups: " + totalResults);
        assertTrue("Should have exception groups", root.children().size() > 0);
        assertTrue("Should have results in groups", totalResults > 0);
    }

    @Test
    public void testInputShapeOutputTypeTable() {
        List<RunResult> results = createMixedResults();
        Query query = QueryCatalog.inputShapeOutputTypeTable().query();
        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);

        System.out.println("=== inputShapeOutputTypeTable ===");
        System.out.println("Aggregations: " + root.aggregations());
        System.out.println("Children: " + root.children().size());

        // Should have children (hierarchical groups)
        assertTrue("Should have groups", root.children().size() > 0);

        // Print each group
        int totalResults = 0;
        for (var entry : root.children().entrySet()) {
            var key = entry.getKey();
            var group = entry.getValue();
            System.out.println("  Group " + key + ": " + group.aggregations());
            System.out.println("    Results: " + group.results().size());
            System.out.println("    SubGroups: " + group.children().size());
            totalResults += group.results().size();
            
            for (var subEntry : group.children().entrySet()) {
                var subKey = subEntry.getKey();
                var subGroup = subEntry.getValue();
                System.out.println("      SubGroup " + subKey + ": " + subGroup.aggregations());
                System.out.println("        Results: " + subGroup.results().size());
                totalResults += subGroup.results().size();
            }
        }

        System.out.println("Total results in groups: " + totalResults);
        assertTrue("Should have results in groups", totalResults > 0);
    }
}
