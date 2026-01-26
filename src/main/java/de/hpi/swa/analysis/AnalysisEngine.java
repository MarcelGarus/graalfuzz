package de.hpi.swa.analysis;

import de.hpi.swa.analysis.operations.*;
import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.FilterSpec;
import de.hpi.swa.analysis.query.NamedQuery;
import de.hpi.swa.analysis.query.Query;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.*;
import java.util.stream.Collectors;

public class AnalysisEngine {

    public record MultiQueryResult(Map<String, ResultGroup<?, ?>> results, Materializer sharedMaterializer) {
        public ResultGroup<?, ?> get(String queryName) {
            return results.get(queryName);
        }

        public Set<String> queryNames() {
            return results.keySet();
        }
    }

    public static ResultGroup<Void, Void> analyze(Query query, List<RunResult> results, Pool pool) {
        if (results.isEmpty()) {
            return ResultGroup.root();
        }

        List<ColumnDef<?>> allColumns = ColumnCollector.buildColumnDefinitions(query);
        Materializer materializer = new Materializer();
        materializer.prepareAll(allColumns, results, pool);

        return analyzeWithMaterializer(query, results, materializer);
    }

    public static MultiQueryResult analyzeMultiple(List<NamedQuery> namedQueries, List<RunResult> results, Pool pool) {
        if (results.isEmpty() || namedQueries.isEmpty()) {
            return new MultiQueryResult(Map.of(), new Materializer());
        }

        Set<ColumnDef<?>> allColumns = new LinkedHashSet<>();
        for (NamedQuery nq : namedQueries) {
            allColumns.addAll(ColumnCollector.buildColumnDefinitions(nq.query()));
        }

        Materializer sharedMaterializer = new Materializer();
        sharedMaterializer.prepareAll(new ArrayList<>(allColumns), results, pool);

        Map<String, ResultGroup<?, ?>> queryResults = new LinkedHashMap<>();
        for (NamedQuery nq : namedQueries) {
            ResultGroup<?, ?> result = analyzeWithMaterializer(nq.query(), results, sharedMaterializer);
            queryResults.put(nq.name(), result);
        }

        return new MultiQueryResult(queryResults, sharedMaterializer);
    }

    private static ResultGroup<Void, Void> analyzeWithMaterializer(Query query, List<RunResult> results,
            Materializer materializer) {
        List<RunResult> filteredResults = results;
        if (query.itemFilter().isPresent()) {
            FilterSpec.RowFilter itemFilter = query.itemFilter().get();
            filteredResults = results.stream()
                    .filter(r -> FilterSpec.testRow(itemFilter, r, materializer))
                    .collect(Collectors.toList());
        }

        ResultGroup<Void, Void> root = Grouping.GroupBuilder.buildTree(
                filteredResults,
                query.groupBy(),
                materializer);

        if (!query.aggregations().isEmpty()) {
            Aggregator.compute(root, query.aggregations(), materializer);
        }

        Scorer.computeGroupScores(root, query.scoringConfig(), materializer);

        if (query.groupFilter().isPresent()) {
            Filterer.filterGroups(root, query.groupFilter().get(), materializer);
        }

        if (!query.itemSort().isEmpty()) {
            Sorter.sortItems(root, query.itemSort(), materializer);
        }

        if (!query.groupSort().isEmpty()) {
            Sorter.sortGroups(root, query.groupSort());
        }

        GroupLimiter.applyGroupLimit(root, query.groupLimit());

        DrillProcessor.applyDrillSpec(root, query.drillSpec());

        Projector.materializeProjections(root, query.groupProjection(), query.itemProjection(), materializer);

        return root;
    }
}
