package de.hpi.swa.analysis.operations;

import java.util.LinkedHashSet;
import java.util.Set;

import de.hpi.swa.analysis.heuristics.KeyHeuristics;
import de.hpi.swa.analysis.heuristics.RowHeuristics;
import de.hpi.swa.analysis.query.AggregationSpec;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.GroupingSpec;
import de.hpi.swa.analysis.query.Query;
import de.hpi.swa.analysis.query.ScoringSpec;

public class ColumnCollector {

    public static LinkedHashSet<ColumnDef<?>> buildColumnDefinitions(Query query) {
        LinkedHashSet<ColumnDef<?>> columns = new LinkedHashSet<>();

        addColumn(columns, ColumnDef.INPUT_SHAPE);
        addColumn(columns, ColumnDef.TRACE);
        addColumn(columns, ColumnDef.OUTPUT_TYPE);
        addColumn(columns, ColumnDef.EXCEPTION_TYPE);
        addColumn(columns, ColumnDef.IS_CRASH);
        addColumn(columns, ColumnDef.COVERAGE_PATH);

        addColumn(columns, new KeyHeuristics.InputShapeSimplicity());
        addColumn(columns, new KeyHeuristics.OutputShapeDiversity());
        addColumn(columns, new KeyHeuristics.InputValidity());
        addColumn(columns, new KeyHeuristics.PathSimplicity());
        addColumn(columns, new KeyHeuristics.CoverageRarity());
        addColumn(columns, RowHeuristics.MINIMAL_INPUT);
        addColumn(columns, RowHeuristics.MINIMAL_OUTPUT);

        for (GroupingSpec spec : query.groupBy()) {
            collectColumnsFromGroupingSpec(columns, spec);
        }

        for (ColumnDef<?> col : query.columns()) {
            addColumn(columns, col);
        }

        ScoringSpec scoring = query.scoringConfig();
        for (ColumnDef<?> col : scoring.heuristicWeights().keySet()) {
            addColumn(columns, col);
        }

        addColumn(columns, scoring.createRowScoreColumn());

        return columns;
    }

    private static void collectColumnsFromGroupingSpec(Set<ColumnDef<?>> columns, GroupingSpec spec) {
        switch (spec) {
            case GroupingSpec.Single<?> single -> addColumn(columns, single.column());
            case GroupingSpec.Composite composite -> {
                for (ColumnDef<?> col : composite.columns()) {
                    addColumn(columns, col);
                }
            }
            case GroupingSpec.Hierarchical hierarchical -> {
                for (var level : hierarchical.levels()) {
                    for (ColumnDef<?> col : level.columns()) {
                        addColumn(columns, col);
                    }
                    for (AggregationSpec<?, ?> agg : level.aggregations()) {
                        addColumn(columns, agg.column());
                    }
                }
            }
        }
    }

    private static void addColumn(Set<ColumnDef<?>> columns, ColumnDef<?> col) {
        columns.add(col);
    }
}
