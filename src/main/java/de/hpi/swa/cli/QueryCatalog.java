package de.hpi.swa.cli;

import de.hpi.swa.analysis.heuristics.RowHeuristics;
import de.hpi.swa.analysis.operations.Scorer;
import de.hpi.swa.analysis.query.*;

import java.util.*;

public final class QueryCatalog {

    private static final Map<String, NamedQuery> QUERIES = new LinkedHashMap<>();

    static {
        register(relevantPairs());
        register(treeList());
        register(inverseTreeList());
        register(observedOutputTypes());
        register(inputTypesToOutputTypes());
        register(inputShapesToOutputTypes());
        register(basicGrouping());
        register(detailedGrouping());
    }

    public static NamedQuery relevantPairs() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");
        var crashCountAgg = AggregationSpec.<Boolean>countIf(ColumnDef.IS_CRASH, "CrashCount", b -> b);

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var minimalOutputSort = new SortSpec<>(RowHeuristics.MINIMAL_OUTPUT, false);

        var groupScoreSort = new GroupSortSpec(Scorer.GROUP_SCORE_AGG, false);

        return NamedQuery.of("relevantPairs", Query.builder()
                .groupByComposite(ColumnDef.INPUT_SHAPE, ColumnDef.COVERAGE_PATH, ColumnDef.OUTPUT_SHAPE,
                        ColumnDef.EXCEPTION_TYPE)
                .aggregations(countAgg, crashCountAgg)
                .groupFilter(FilterSpec.GroupFilter.or(
                        FilterSpec.GroupFilter.predicate("CrashCount", c -> c == null || ((Integer) c) == 0),
                        FilterSpec.GroupFilter.predicate("Count", c -> c == null)
                ))
                .itemSort(minimalInputSort, minimalOutputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(3))
                .build());
    }

    public static NamedQuery treeList() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");
        var crashCountAgg = AggregationSpec.<Boolean>countIf(ColumnDef.IS_CRASH, "CrashCount", b -> b);

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var groupScoreSort = new GroupSortSpec(Scorer.GROUP_SCORE_AGG, false);

        var notAllCrashesFilter = FilterSpec.GroupFilter.predicate2("Count", "CrashCount",
                (count, crashCount) -> {
                    if (count == null || crashCount == null)
                        return true;
                    return !count.equals(crashCount);
                });

        return NamedQuery.of("treeList", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE, ColumnDef.OUTPUT_TYPE)
                .aggregations(countAgg, crashCountAgg)
                .groupFilter(notAllCrashesFilter)
                .itemSort(minimalInputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(10))
                .build());
    }

    public static NamedQuery inverseTreeList() {
        var countAgg = AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count");
        var inputShapesAgg = AggregationSpec.distinctSet(ColumnDef.INPUT_SHAPE, "InputShapes");

        var minimalInputSort = new SortSpec<>(RowHeuristics.MINIMAL_INPUT, false);
        var groupScoreSort = new GroupSortSpec(Scorer.GROUP_SCORE_AGG, false);

        return NamedQuery.of("inverseTreeList", Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE, ColumnDef.INPUT_SHAPE)
                .aggregations(countAgg, inputShapesAgg)
                .itemSort(minimalInputSort)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.All(10))
                .build());
    }

    public static NamedQuery observedOutputTypes() {
        var countAgg = AggregationSpec.count(ColumnDef.OUTPUT_TYPE, "Count");
        var inputTypesAgg = AggregationSpec.distinctSet(ColumnDef.INPUT_TYPE, "InputTypes");
        var inputTypeCountAgg = AggregationSpec.uniqueCount(ColumnDef.INPUT_TYPE, "UniqueInputTypes");

        return NamedQuery.of("observedOutputTypes", Query.builder()
                .groupBy(ColumnDef.OUTPUT_TYPE)
                .aggregations(countAgg, inputTypesAgg, inputTypeCountAgg)
                .groupSortByAggregation("Count", false)
                .drill(new DrillSpec.LeafsOnly(5))
                .build());
    }

    public static NamedQuery inputTypesToOutputTypes() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_TYPE, "Count");
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var outputTypeCountAgg = AggregationSpec.uniqueCount(ColumnDef.OUTPUT_TYPE, "UniqueOutputTypes");

        return NamedQuery.of("inputTypesToOutputTypes", Query.builder()
                .groupBy(ColumnDef.INPUT_TYPE)
                .aggregations(countAgg, outputTypesAgg, outputTypeCountAgg)
                .groupSortByAggregation("Count", false)
                .drill(new DrillSpec.LeafsOnly(5))
                .build());
    }

    public static NamedQuery inputShapesToOutputTypes() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");
        var outputTypesAgg = AggregationSpec.distinctSet(ColumnDef.OUTPUT_TYPE, "OutputTypes");
        var outputTypeCountAgg = AggregationSpec.uniqueCount(ColumnDef.OUTPUT_TYPE, "UniqueOutputTypes");

        var groupScoreSort = new GroupSortSpec(Scorer.GROUP_SCORE_AGG, false);

        return NamedQuery.of("inputShapesToOutputTypes", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE)
                .aggregations(countAgg, outputTypesAgg, outputTypeCountAgg)
                .groupSort(groupScoreSort)
                .drill(new DrillSpec.LeafsOnly(5))
                .build());
    }

    public static NamedQuery basicGrouping() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");
        var errorCountAgg = AggregationSpec.<String>count(ColumnDef.OUTPUT_TYPE, "ErrorCount");

        return NamedQuery.of("basic", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE)
                .aggregations(countAgg, errorCountAgg)
                .drill(new DrillSpec.None())
                .build());
    }

    public static NamedQuery detailedGrouping() {
        var countAgg = AggregationSpec.count(ColumnDef.INPUT_SHAPE, "Count");
        var errorCountAgg = AggregationSpec.<String>count(ColumnDef.OUTPUT_TYPE, "ErrorCount");

        return NamedQuery.of("detailed", Query.builder()
                .groupBy(ColumnDef.INPUT_SHAPE, ColumnDef.OUTPUT_TYPE, ColumnDef.TRACE, ColumnDef.EXCEPTION_TYPE)
                .aggregations(countAgg, errorCountAgg)
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
