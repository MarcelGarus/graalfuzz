package de.hpi.swa.analysis.query;

import java.util.Comparator;

public record SortSpec<T extends Comparable<T>>(ColumnDef<T> column, boolean ascending, Comparator<T> comparator) {
    public SortSpec(ColumnDef<T> column, boolean ascending) {
        this(column, ascending, Comparator.nullsLast(Comparable::compareTo));
    }

    public static <T extends Comparable<T>> SortSpec<T> asc(ColumnDef<T> col) {
        return new SortSpec<>(col, true);
    }

    public static <T extends Comparable<T>> SortSpec<T> desc(ColumnDef<T> col) {
        return new SortSpec<>(col, false);
    }
}
