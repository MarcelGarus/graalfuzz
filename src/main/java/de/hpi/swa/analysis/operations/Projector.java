package de.hpi.swa.analysis.operations;

import de.hpi.swa.analysis.operations.Grouping.ResultGroup;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.ProjectionSpec;
import de.hpi.swa.analysis.query.ColumnDef.AggregationRef;
import de.hpi.swa.generator.Runner.RunResult;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Projector {

    public static void materializeProjections(ResultGroup<?, ?> root, ProjectionSpec groupProjection,
            ProjectionSpec itemProjection, Materializer materializer) {
        root.forEachGroup(group -> {
            if (!groupProjection.selectedColumns().isEmpty()) {
                for (ColumnDef<?> col : groupProjection.selectedColumns()) {
                    Object value = getGroupColumnValue(group, col, materializer);
                    if (value != null) {
                        group.projectedGroupData().put(col, value);
                    }
                }
            }

            if (!itemProjection.selectedColumns().isEmpty()) {
                for (RunResult row : group.results()) {
                    Map<ColumnDef<?>, Object> rowData = group.projectedRowData()
                            .computeIfAbsent(row, k -> new LinkedHashMap<>());

                    for (ColumnDef<?> col : itemProjection.selectedColumns()) {
                        Object value = materializer.materialize(row, col);
                        rowData.put(col, value);
                    }
                }
            }
        });
    }

    private static Object getGroupColumnValue(ResultGroup<?, ?> group, ColumnDef<?> col, Materializer materializer) {
        LinkedHashMap<ColumnDef<?>, Object> allKeys = group.allGroupKeys();
        if (allKeys.containsKey(col)) {
            return allKeys.get(col);
        }

        if (col instanceof AggregationRef<?> && group.aggregations().containsKey(col.name())) {
            return group.aggregations().get(col.name());
        }

        if (!group.results().isEmpty()) {
            // Only in this case it is possible that some rows in this group may have a
            // different value for this column
            return materializer.materialize(group.results().get(0), col);
        }

        return null;
    }
}
