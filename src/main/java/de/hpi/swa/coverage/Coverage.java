package de.hpi.swa.coverage;

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.source.SourceSection;

public final class Coverage {

    public final Set<SourceSection> loaded = new HashSet<>();
    public final Set<SourceSection> covered = new HashSet<>();

    private Set<SourceSection> nonCoveredSections() {
        final HashSet<SourceSection> nonCovered = new HashSet<>();
        nonCovered.addAll(loaded);
        nonCovered.removeAll(covered);
        return nonCovered;
    }

    Set<Integer> nonCoveredLineNumbers() {
        Set<Integer> linesNotCovered = new HashSet<>();
        for (SourceSection ss : nonCoveredSections()) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                linesNotCovered.add(i);
            }
        }
        return linesNotCovered;
    }

    Set<Integer> loadedLineNumbers() {
        Set<Integer> loadedLines = new HashSet<>();
        for (SourceSection ss : loaded) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                loadedLines.add(i);
            }
        }
        return loadedLines;
    }
}
