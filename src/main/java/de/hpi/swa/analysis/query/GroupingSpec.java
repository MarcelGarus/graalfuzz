package de.hpi.swa.analysis.query;

import java.util.Arrays;
import java.util.List;

public sealed interface GroupingSpec {

    List<ColumnDef<?>> columns();

    record Single<T>(ColumnDef<T> column) implements GroupingSpec {
        @Override
        public List<ColumnDef<?>> columns() {
            return List.of(column);
        }

        @Override
        public final String toString() {
            return column.name();
        }
    }

    record Composite(List<ColumnDef<?>> columns) implements GroupingSpec {
        public Composite {
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Composite grouping requires at least one column");
            }
            columns = List.copyOf(columns);
        }

        public static Composite of(ColumnDef<?>... columns) {
            return new Composite(Arrays.asList(columns));
        }

        @Override
        public List<ColumnDef<?>> columns() {
            return columns;
        }

        @Override
        public final String toString() {
            return String.join(" | ", columns.stream().map(ColumnDef::name).toList());
        }
    }

    static <T> Single<T> single(ColumnDef<T> column) {
        return new Single<>(column);
    }

    static Composite composite(ColumnDef<?>... columns) {
        return Composite.of(columns);
    }
}
