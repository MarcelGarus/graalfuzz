package de.hpi.swa.analysis.grouping;

public sealed interface GroupingStrategy {

    record NoGroups() implements GroupingStrategy {
    }

    record CompositeGroups() implements GroupingStrategy {
    }
}
