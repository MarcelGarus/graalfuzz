package de.hpi.swa.analysis;

import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.*;
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
 * Tests for the AnalysisEngine with various query configurations.
 * Uses mock RunResults to verify grouping, aggregation, filtering, and sorting
 * behavior.
 */
public class AnalysisEngineTest {

    // Helper to create a mock RunResult
    private static RunResult mockResult(String inputType, String outputType, boolean crashed) {
        Universe universe = new Universe();
        Value input = createMockValue(inputType, universe);
        FunctionResult output = crashed
                ? new FunctionResult.Crash("TestError", List.of())
                : new FunctionResult.Normal(outputType, "testValue");
        Trace trace = new Trace();
        return new RunResult(universe, input, output, trace);
    }

    private static Value createMockValue(String type, Universe universe) {
        return switch (type) {
            case "int" -> new Value.Int(42);
            case "float", "double" -> new Value.Double(3.14);
            case "string" -> new Value.StringValue("test");
            case "bool", "boolean" -> new Value.Boolean(true);
            case "object" -> new Value.ObjectValue(universe.createObject());
            default -> new Value.Null();
        };
    }

    @Test
    public void testBasicGroupingBySingleColumn() {
        // Create test data with different output types
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("float", "int", false),
                mockResult("string", "bool", false));

        Query query = Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        // Should have 3 groups: string, int, bool
        assertEquals(3, root.children().size());

        for (var child : root.children().values()) {
            assertTrue(child.aggregations().containsKey("Count"));
            int count = (int) child.aggregations().get("Count");
            assertTrue(count > 0);
        }
    }

    @Test
    public void testCompositeGrouping() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "int", false),
                mockResult("float", "string", false));

        Query query = Query.builder()
                .groupByComposite(ColumnDef.INPUT_TYPE, ColumnDef.OUTPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        // Should have 3 groups: (int,string), (int,int), (float,string)
        assertEquals(3, root.children().size());
    }

    @Test
    public void testAggregationDistributiveSum() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "string", false));

        Query query = Query.builder()
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "TotalCount"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        assertEquals(3, root.aggregations().get("TotalCount"));
    }

    @Test
    public void testDistinctSetAggregation() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "int", false),
                mockResult("float", "string", false),
                mockResult("float", "bool", false));

        Query query = Query.builder()
                .aggregations(AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        @SuppressWarnings("unchecked")
        Set<String> outputTypes = (Set<String>) root.aggregations().get("OutputTypes");
        assertEquals(3, outputTypes.size());
        assertTrue(outputTypes.contains("string"));
        assertTrue(outputTypes.contains("int"));
        assertTrue(outputTypes.contains("bool"));
    }

    @Test
    public void testGroupFiltering() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("float", "int", false) // Only 1 result
        );

        // Filter to keep only groups with count > 1
        Query query = Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count"))
                .groupFilter(FilterSpec.GroupFilter.predicate("Count",
                        count -> count != null && ((Integer) count) > 1))
                .build();

        Pool pool = new Pool();
        ResultGroup<?, ?> root = AnalysisEngine.analyze(query, results, pool);

        // Only the "int" group should remain (count=3), "float" filtered out (count=1)
        assertEquals(1, root.children().size());
        root.children().values().forEach(group -> assertEquals(3, group.aggregations().get("Count")));
        root.children().keySet().forEach(key -> assertTrue(((GroupKey.Single<?>) key).value().equals("int")));
    }

    @Test
    public void testGroupSorting() {
        List<RunResult> results = List.of(
                mockResult("int", "a", false),
                mockResult("int", "a", false),
                mockResult("int", "a", false),
                mockResult("float", "b", false),
                mockResult("float", "b", false),
                mockResult("string", "c", false));

        Query query = Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count"))
                .groupSortByAggregation("Count", false)
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        List<Integer> counts = root.children().values().stream()
                .map(g -> (Integer) g.aggregations().get("Count"))
                .toList();

        assertEquals(3, counts.size());
        assertEquals(Integer.valueOf(3), counts.get(0));
        assertEquals(Integer.valueOf(2), counts.get(1));
        assertEquals(Integer.valueOf(1), counts.get(2));
    }

    @Test
    public void testHierarchicalGrouping() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "int", false),
                mockResult("double", "string", false),
                mockResult("double", "int", false),
                mockResult("bool", "bool", false),
                mockResult("bool", "null", false),
                mockResult("string", "string", false),
                mockResult("string", "null", false));

        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_TYPE, "RowCount");
        var outputTypesRef = ColumnDef.AggregationRef.<Set<String>>of("OutputTypes");

        var hierarchicalGrouping = GroupingSpec.Hierarchical.builder()
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(outputTypesRef)
                        .aggregations(
                                AggregationSpec.distinctSet(ColumnDef.INPUT_TYPE, "InputTypes"),
                                AggregationSpec.sum(ColumnDef.AggregationRef.<Set<String>>of("RowCount"),
                                        "Count"))
                        .build())
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(ColumnDef.INPUT_TYPE)
                        .aggregations(outputTypesAgg, countAgg)
                        .build())
                .build();

        Query query = Query.builder()
                .groupBy(hierarchicalGrouping)
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        // Expected level 1:
        // - string | int <- int | float
        // - bool | null <- bool
        // - string | null <- string
        assertTrue(root.children().size() == 3);
        for (var child : root.children().values()) {
            var key = (GroupKey.Composite) child.groupKey();
            assertTrue(key.parts().size() == 1);
            @SuppressWarnings("unchecked")
            var outputTypes = (Set<String>) key.parts().get(0).value();
            assertTrue(outputTypes.size() == 2);

            // Aggregations at level 1
            assertTrue(child.aggregations().containsKey("InputTypes"));
            assertTrue(child.aggregations().containsKey("Count"));

            // Expected level 2:
            // - int | float -> string | int
            // - bool -> bool | null
            // - string -> string | null
            if (outputTypes.contains("int") && outputTypes.contains("string")) {
                var inputTypes = (Set<?>) child.aggregations().get("InputTypes");
                assertTrue(inputTypes instanceof Set);
                assertEquals(2, ((Set<?>) inputTypes).size());
                assertTrue(inputTypes.contains("int"));
                assertTrue(inputTypes.contains("double"));

                // int and double should be regrouped here
                assertEquals("Expected 2 children (int, double) for {string,int} group", 2, child.children().size());
                assertTrue(child.children().keySet().stream()
                        .allMatch(k -> {
                            var part = ((GroupKey.Composite) k).parts().get(0).value();
                            return part.equals("int") || part.equals("double");
                        }));
            } else if (outputTypes.contains("bool") && outputTypes.contains("null")) {
                // bool
                var inputTypes = (Set<?>) child.aggregations().get("InputTypes");
                assertTrue(inputTypes instanceof Set);
                assertEquals(1, ((Set<?>) inputTypes).size());
                assertTrue(inputTypes.contains("boolean"));

                assertTrue(child.children().size() == 1);
                assertTrue(child.children().keySet().stream()
                        .allMatch(k -> {
                            var part = ((GroupKey.Composite) k).parts().get(0).value();
                            return part.equals("boolean");
                        }));
            } else if (outputTypes.contains("string") && outputTypes.contains("null")) {
                // string
                var inputTypes = (Set<?>) child.aggregations().get("InputTypes");
                assertTrue(inputTypes instanceof Set);
                assertEquals(1, ((Set<?>) inputTypes).size());
                assertTrue(inputTypes.contains("string"));

                assertTrue(child.children().size() == 1);
                assertTrue(child.children().keySet().stream()
                        .allMatch(k -> {
                            var part = ((GroupKey.Composite) k).parts().get(0).value();
                            return part.equals("string");
                        }));
            } else {
                fail("Unexpected output types group: " + outputTypes);
            }
        }

        // Top-level groups have children (bottom level groups), not direct results
        for (var topGroup : root.children().values()) {
            assertFalse(topGroup.children().isEmpty());
            // Bottom level groups have results
            for (var bottomGroup : topGroup.children().values()) {
                assertFalse(bottomGroup.results().isEmpty());
            }
        }
    }

    @Test
    public void testHierarchicalGroupingWithPerLevelSorting() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("float", "string", false),
                mockResult("bool", "int", false));

        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_TYPE, "RowCount");
        var outputTypesRef = ColumnDef.AggregationRef.<Set<String>>of("OutputTypes");
        var sumCountsAgg = AggregationSpec.<Integer>sum(ColumnDef.AggregationRef.<Integer>of("RowCount"), "TotalCount");

        var hierarchicalGrouping = GroupingSpec.Hierarchical.builder()
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(outputTypesRef)
                        .aggregations(sumCountsAgg)
                        .groupSort(new GroupSortSpec("TotalCount", false))
                        .build())
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(ColumnDef.INPUT_TYPE)
                        .aggregations(outputTypesAgg, countAgg)
                        .groupSort(new GroupSortSpec("RowCount", false))
                        .build())
                .build();

        Query query = Query.builder()
                .groupBy(hierarchicalGrouping)
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        assertFalse(root.children().isEmpty());

        // Check that top-level groups are sorted by TotalCount descending
        List<Integer> totalCounts = root.children().values().stream()
                .map(g -> (Integer) g.aggregations().get("TotalCount"))
                .toList();
        for (int i = 1; i < totalCounts.size(); i++) {
            assertTrue(totalCounts.get(i - 1) >= totalCounts.get(i));
        }

        // Check that second-level groups are sorted by RowCount descending
        for (var topGroup : root.children().values()) {
            List<Integer> rowCounts = topGroup.children().values().stream()
                    .map(g -> (Integer) g.aggregations().get("RowCount"))
                    .toList();
            for (int i = 1; i < rowCounts.size(); i++) {
                assertTrue(rowCounts.get(i - 1) >= rowCounts.get(i));
            }
        }
    }

    @Test
    public void testItemDeduplication() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", false),
                mockResult("int", "int", false));

        Query query = Query.builder()
                .deduplicateBy(ColumnDef.INPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        assertEquals(1, (int) root.aggregations().get("Count"));
    }

    @Test
    public void testItemFiltering() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "string", true),
                mockResult("float", "int", false));

        Query query = Query.builder()
                .itemFilter(FilterSpec.RowFilter.predicate(
                        ColumnDef.EXCEPTION_TYPE,
                        exType -> exType == null))
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        assertEquals(2, (int) root.aggregations().get("Count"));
    }

    @Test
    public void testEmptyResults() {
        List<RunResult> results = List.of();

        Query query = Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count"))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        assertTrue(root.children().isEmpty());
        assertTrue(root.results().isEmpty());
    }

    @Test
    public void testDrillSpecLeafsOnly() {
        List<RunResult> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(mockResult("int", "string", false));
        }

        Query query = Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE, ColumnDef.OUTPUT_TYPE)
                .drill(new DrillSpec.LeafsOnly(3))
                .build();

        Pool pool = new Pool();
        var root = AnalysisEngine.analyze(query, results, pool);

        for (var child : root.children().values()) {
            assertTrue(child.results().isEmpty());
            for (var grandChild : child.children().values()) {
                assertTrue(grandChild.results().size() <= 3);
            }
        }
    }

    @Test
    public void testMultipleQueries() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("int", "float", false),
                mockResult("float", "int", false));

        Query query1 = Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count"))
                .build();

        Query query2 = Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count"))
                .build();

        List<NamedQuery> namedQueries = List.of(
                NamedQuery.of("byInput", query1),
                NamedQuery.of("byOutput", query2));

        Pool pool = new Pool();
        var multiResult = AnalysisEngine.analyzeMultiple(namedQueries, results, pool);

        assertEquals(2, multiResult.queryNames().size());
        assertNotNull(multiResult.get("byInput"));
        assertNotNull(multiResult.get("byOutput"));

        assertEquals(2, multiResult.get("byInput").children().size());
        assertEquals(3, multiResult.get("byOutput").children().size());
    }

    @Test
    public void testNullSafeSortingWithMissingAggregations() {
        List<RunResult> results = List.of(
                mockResult("int", "string", false),
                mockResult("float", "int", false));

        Query query = Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE)
                .aggregations(AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count"))
                .groupSortByAggregation("NonExistent", false)
                .build();

        Pool pool = new Pool();

        var root = AnalysisEngine.analyze(query, results, pool);
        assertEquals(2, root.children().size());
    }
}
