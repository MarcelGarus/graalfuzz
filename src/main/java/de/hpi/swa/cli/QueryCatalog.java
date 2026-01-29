package de.hpi.swa.cli;

import de.hpi.swa.analysis.heuristics.RowHeuristics;
import de.hpi.swa.analysis.operations.GroupScorer;
import de.hpi.swa.analysis.query.*;

import java.util.*;

public final class QueryCatalog {

    private static final Map<String, NamedQuery> QUERIES = new LinkedHashMap<>();

    /*
     * Errors that indicate an invalid input was passed.
     * They can be excluded when trying to observe "valid" input output examples.
     */
    public static final Set<String> DEFAULT_EXCLUDED_EXCEPTIONS = Set.of(
            // python: Other interesting errors are:
            // - IndexError, ValueError
            // but they don't always indicate invalid inputs
            "AttributeError",
            "TypeError",
            "KeyError",

            // js: Other interesting errors are:
            // - RangeError, SyntaxError, AggregateError, (ReferenceError)
            // but they don't always indicate invalid inputs
            "Uncaught TypeError");

    static {
        register(relevantPairs());
        register(treeList());
        register(inverseTreeList());
        register(inputShapeUnion());
        register(outputTypeUnion());
        register(observedSignature());
        register(inputShapeToOutputShapes());
        register(outputTypeToInputShapes());
        register(inputShapeOutputTypeTable());
        register(exceptionExamples());
        register(validExamples());
        register(allExamples());
        register(basicGrouping());
        register(detailedGrouping());
    }

    /**
     * Input Shape Union
     * Returns the union of all input shapes (excluding fully-erroring shapes).
     * No grouping, just aggregate at root level.
     */
    public static NamedQuery inputShapeUnion() {
        var inputShapesAgg = AggregationSpec.propertyShapeUnion(ColumnDef.INPUT_SHAPE, "InputShapes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("inputShapeUnion", Query.builder()
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(inputShapesAgg, countAgg)
                .drill(new DrillSpec.None())
                .build());
    }

    /**
     * Output Type Union
     * Returns the union of all output types (including errors except excluded
     * ones).
     * No grouping, just aggregate at root level.
     */
    public static NamedQuery outputTypeUnion() {
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count");

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("outputTypeUnion", Query.builder()
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(outputTypesAgg, countAgg)
                .drill(new DrillSpec.None())
                .build());
    }

    /**
     * Observed Function Signature
     * Union over both input shapes and output types.
     * No grouping, aggregates at root level.
     */
    public static NamedQuery observedSignature() {
        var inputShapesAgg = AggregationSpec.propertyShapeUnion(ColumnDef.INPUT_SHAPE, "InputShapes");
        var inputTypesAgg = AggregationSpec.distinctSet(ColumnDef.INPUT_TYPE, "InputTypes");
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("observedSignature", Query.builder()
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(inputShapesAgg, inputTypesAgg, outputTypesAgg, countAgg)
                .drill(new DrillSpec.None())
                .build());
    }

    /**
     * Group by Input Shape → Union Output Shapes
     * Groups by input shape and computes the union of output shapes for each.
     * Excludes excluded exception types.
     */
    public static NamedQuery inputShapeToOutputShapes() {
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("inputShapeToOutputShapes", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(outputTypesAgg, countAgg)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.LeafsOnly(3))
                .build());
    }

    /**
     * Group by Output Type → Union Input Shapes
     * Groups by output type and computes the union of input shapes for each.
     */
    public static NamedQuery outputTypeToInputShapes() {
        var inputShapesAgg = AggregationSpec.propertyShapeUnion(ColumnDef.INPUT_SHAPE, "InputShapes");
        var inputTypesAgg = AggregationSpec.distinctSet(ColumnDef.INPUT_TYPE, "InputTypes");
        var countAgg = AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count");

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("outputTypeToInputShapes", Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(inputShapesAgg, inputTypesAgg, countAgg)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.LeafsOnly(3))
                .build());
    }

    /**
     * Input Shape to Output Type Table (Hierarchical Grouping)
     * 
     * Groups inputs that produce the SAME set of output types together.
     * Uses hierarchical post-aggregation re-grouping:
     * 
     * 1. First groups by InputShape
     * 2. Aggregates the OutputTypes for each InputShape
     * 3. Re-groups by the aggregated OutputTypes (inputs with same output set are
     * merged)
     * 
     * Result format:
     * - inputShape1 | inputShape2 -> outputType1 | outputType2 (count)
     * - inputShape3 | inputShape4 -> outputType1 | outputType2 (count)
     * Example: int | float -> string | null and bool -> string [NOT NULL!]
     * -> meaning: for both int and float exist inputs that produce string or bool
     * -> & bool always returns a string, NEVER null
     * e.g. (0 -> null), (1 -> "abc"), (0.0 -> null), (1.0 -> "abc")
     * AND (true -> "def"), (false -> "ghi")
     * 
     * Steps:
     * 1. Group by InputShape first (subGroup)
     * e.g. int -> [string, null], float -> [string, null], bool -> [string]
     * 2. Aggregate distinct OutputTypes per InputShape
     * e.g. int -> string | null, float -> string | null, bool -> string
     * 3. Group by the aggregated OutputShapes
     * e.g. (out: string | null, in: [int, float]), (out: string -> in: [bool])
     * 4. Aggregate InputShapes per OutputType set group
     * e.g. (out: string | null, in: int) | float, (out: string, in: bool)
     * Result: int -> string | null, bool -> string
     * 
     * Example 2: bool | int -> string, float -> float | string
     * e.g. (True -> "a"), (False -> "b"), (0 -> "a"), (1 -> "b"),
     * AND (0.0 -> "a"), (1.0 -> "b"), (2.0 -> 2.0)
     * 
     * Steps:
     * 1. Group by InputShape first (subGroup)
     * e.g. bool -> [string], int -> [string], float -> [string, float]
     * 2. Aggregate distinct OutputTypes per InputShape
     * e.g. bool -> string, int -> string, float -> string | float
     * 3. Group by the aggregated OutputShapes
     * e.g. (out: string, in: [bool, int]), (out: string | float, in: [float])
     * 4. Aggregate InputShapes per OutputType set group
     * e.g. (out: string, in: bool | int), (out: string | float, in: float)
     * Result: bool | int -> string, float -> string | float
     */
    public static NamedQuery inputShapeOutputTypeTable() {
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var inputShapesAgg = AggregationSpec.propertyShapeUnion(ColumnDef.INPUT_SHAPE, "InputShapes");
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "RowCount");
        var sumCountsAgg = AggregationSpec.<Integer>sum(ColumnDef.AggregationRef.<Integer>of("RowCount"),
                "Count");

        var countSort = new GroupSortSpec("Count", false);
        var rowCountSort = new GroupSortSpec("RowCount", false);

        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        var outputTypesRef = ColumnDef.AggregationRef.<Set<String>>of("OutputTypes");

        var hierarchicalGrouping = GroupingSpec.Hierarchical.builder()
                // Level 0: Top level - groups by aggregated OutputTypes (executed last)
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(outputTypesRef)
                        .aggregations(inputShapesAgg, sumCountsAgg)
                        .groupSort(countSort)
                        .build())
                // Level 1: Bottom level - groups RunResults by InputShape (executed first)
                .level(GroupingSpec.Hierarchical.Level.builder()
                        .columns(ColumnDef.INPUT_SHAPE)
                        .aggregations(outputTypesAgg, countAgg)
                        .groupSort(rowCountSort)
                        .build())
                .build();

        return NamedQuery.of("inputShapeOutputTypeTable", Query.builder()
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .groupBy(hierarchicalGrouping)
                .drill(new DrillSpec.LeafsOnly(3))
                .build());
    }

    /**
     * Groups by output value to find out which inputs lead to which outputs.
     * Provides counts for each group.
     */
    public static NamedQuery outputValues() {
        var countAgg = AggregationSpec.count(ColumnDef.EXCEPTION_TYPE, "Count");
        var inputShapeAgg = AggregationSpec.propertyShapeUnion(ColumnDef.INPUT_SHAPE, "InputShapes");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);

        var isCrash = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType != null && !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("outputValues", Query.builder()
                .groupBy(ColumnDef.OUTPUT_VALUE)
                .itemFilter(isCrash)
                .aggregations(inputShapeAgg, countAgg)
                .itemSort(minimalInputSort)
                .groupSortByAggregation("Count", false)
                .drill(new DrillSpec.LeafsOnly(5))
                .build());
    }

    /**
     * Groups by exception type (excluding AttributeError, TypeError).
     * Provides count and one example for each exception type.
     */
    public static NamedQuery exceptionExamples() {
        var countAgg = AggregationSpec.count(ColumnDef.EXCEPTION_TYPE, "Count");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);

        var isCrash = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType != null && !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("exceptionExamples", Query.builder()
                .groupByComposite(ColumnDef.EXCEPTION_TYPE, ColumnDef.INPUT_SHAPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(isCrash)
                .aggregations(countAgg)
                .itemSort(minimalInputSort)
                .groupSortByAggregation("Count", false)
                .drill(new DrillSpec.LeafsOnly(1))
                .build());
    }

    /**
     * Returns one example per unique (input type, output type) pair.
     * Excludes all crashes.
     */
    public static NamedQuery validExamples() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var minimalOutputSort = new SortSpec<>(RowHeuristics.MINIMAL_OUTPUT, false);

        var notCrash = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null);

        return NamedQuery.of("validExamples", Query.builder()
                .groupByComposite(ColumnDef.INPUT_TYPE, ColumnDef.COVERAGE_PATH)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notCrash)
                .aggregations(countAgg)
                .itemSort(minimalInputSort, minimalOutputSort)
                .groupSortByAggregation("Count", false)
                        .drill(new DrillSpec.All(3))
                .build());
    }

    /**
     * Returns all examples grouped by (input shape, output type).
     * Includes both valid and non-excluded exception cases.
     */
    public static NamedQuery allExamples() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var minimalOutputSort = new SortSpec<>(RowHeuristics.MINIMAL_OUTPUT, false);

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("allExamples", Query.builder()
                .groupByComposite(ColumnDef.INPUT_SHAPE, ColumnDef.OUTPUT_TYPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg)
                .itemSort(minimalInputSort, minimalOutputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.LeafsOnly(2))
                .build());
    }

    public static NamedQuery relevantPairs() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var minimalOutputSort = new SortSpec<>(RowHeuristics.MINIMAL_OUTPUT, false);

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        // Filter out groups where ALL results are crashes (non-excluded crashes like
        // ZeroDivisionError)
        var notCrashGroup = FilterSpec.GroupFilter.keyPredicate(ColumnDef.OUTPUT_TYPE,
                outputType -> outputType == null || !outputType.equals("Crash"));

        return NamedQuery.of("relevantPairs", Query.builder()
                .groupByComposite(ColumnDef.INPUT_SHAPE, ColumnDef.COVERAGE_PATH, ColumnDef.OUTPUT_TYPE,
                        ColumnDef.EXCEPTION_TYPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg)
                .groupFilter(notCrashGroup)
                .itemSort(minimalInputSort, minimalOutputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(3))
                .build());
    }

    public static NamedQuery treeList() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("treeList", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE, ColumnDef.OUTPUT_TYPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg)
                .itemSort(minimalInputSort)
                .groupSort(groupScoreSort)
                        .drill(new DrillSpec.LeafsOnly(10))
                .build());
    }

    public static NamedQuery inverseTreeList() {
        var countAgg = AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count");
        var inputShapesAgg = AggregationSpec.distinctSet(ColumnDef.INPUT_SHAPE, "InputShapes");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("inverseTreeList", Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE, ColumnDef.INPUT_SHAPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg, inputShapesAgg)
                .itemSort(minimalInputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(10))
                .build());
    }

    public static NamedQuery basicGrouping() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("basic", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.LeafsOnly(3))
                .build());
    }

    public static NamedQuery detailedGrouping() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");

        var groupScoreSort = new GroupSortSpec(GroupScorer.GROUP_SCORE_AGG, false);

        // Filter out excluded exceptions (TypeError, AttributeError, etc.) that
        // indicate invalid inputs
        var notExcludedError = FilterSpec.RowFilter.predicate(
                ColumnDef.EXCEPTION_TYPE,
                exType -> exType == null || !DEFAULT_EXCLUDED_EXCEPTIONS.contains(exType));

        return NamedQuery.of("detailed", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE, ColumnDef.OUTPUT_TYPE, ColumnDef.COVERAGE_PATH,
                        ColumnDef.EXCEPTION_TYPE)
                .deduplicateBy(ColumnDef.INPUT_VALUE)
                .itemFilter(notExcludedError)
                .aggregations(countAgg)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(5))
                .build());
    }

    private static void register(NamedQuery query) {
        QUERIES.put(query.name(), query);
    }

    public static NamedQuery get(String name) {
        return QUERIES.get(name);
    }

    public static List<NamedQuery> getAll() {
        return new ArrayList<>(QUERIES.values());
    }

    public static List<String> getAvailableNames() {
        return new ArrayList<>(QUERIES.keySet());
    }

    public static List<NamedQuery> getByNames(List<String> names) {
        List<NamedQuery> result = new ArrayList<>();
        for (String name : names) {
            NamedQuery q = QUERIES.get(name);
            if (q != null) {
                result.add(q);
            }
        }
        return result;
    }
}
