package de.hpi.swa.analysis.query;

import java.util.List;

public record ProjectionSpec(List<ColumnDef<?>> selectedColumns) {
    public ProjectionSpec {
        selectedColumns = List.copyOf(selectedColumns);
    }
}