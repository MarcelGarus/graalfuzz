package de.hpi.swa.analysis.grouping;

import java.util.List;

public sealed interface GroupingStrategy {

    record NoGroups() implements GroupingStrategy {
    }

    record CompositeGroups(List<GroupKey> groups) implements GroupingStrategy {
    }

    record CompositeAllGroups() implements GroupingStrategy {
    }
}
