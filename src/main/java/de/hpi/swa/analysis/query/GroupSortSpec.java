package de.hpi.swa.analysis.query;

public record GroupSortSpec(String aggregationName, boolean ascending) {

    public static GroupSortSpec asc(String aggregationName) {
        return new GroupSortSpec(aggregationName, true);
    }

    public static GroupSortSpec desc(String aggregationName) {
        return new GroupSortSpec(aggregationName, false);
    }

    @SuppressWarnings("unchecked")
    public <T> int compare(T v1, T v2) {
        if (v1 == null && v2 == null)
            return 0;
        if (v1 == null)
            return ascending ? 1 : -1;
        if (v2 == null)
            return ascending ? -1 : 1;

        int c;
        if (v1 instanceof Number n1 && v2 instanceof Number n2) {
            c = Double.compare(n1.doubleValue(), n2.doubleValue());
        } else if (v1 instanceof Comparable<?> c1) {
            c = ((Comparable<Object>) c1).compareTo((Object) v2);
        } else {
            c = v1.toString().compareTo(v2.toString());
        }

        return ascending ? c : -c;
    }
}
