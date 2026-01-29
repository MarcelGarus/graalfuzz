package de.hpi.swa.analysis.query;

public record NamedQuery(String name, Query query) {

    public NamedQuery {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Query name must not be null or blank");
        }
        if (query == null) {
            throw new IllegalArgumentException("Query must not be null");
        }
    }

    public static NamedQuery of(String name, Query query) {
        return new NamedQuery(name, query);
    }

    public static NamedQuery of(String name, Query.Builder builder) {
        return new NamedQuery(name, builder.build());
    }
}
