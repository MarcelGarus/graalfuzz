package de.hpi.swa.coverage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class Coverage {

    private final Set<SourceSection> loaded = new HashSet();
    private final Set<SourceSection> covered = new HashSet();

    public void addLoaded(SourceSection section) {
        loaded.add(section);
    }

    public void addCovered(SourceSection section) {
        covered.add(section);
    }

    public Set<SourceSection> getLoaded() {
        return loaded;
    }

    public Set<SourceSection> getCovered() {
        return covered;
    }

    public Set<SourceSection> getNonCovered() {
        final HashSet<SourceSection> nonCovered = new HashSet<>();
        nonCovered.addAll(loaded);
        nonCovered.removeAll(covered);
        return nonCovered;
    }

    public void clear() {
        covered.clear();
    }

    public void printSummary() {
        // System.out.print("Covered " + covered.size() + " / " + loaded.size() + " expressions: ");
        System.out.print("Coverage: ");
        for (var section : loaded) {
            var c = covered.contains(section) ? 'X' : '_';
            System.out.print(c);
        }
        System.out.println();
    }

    public void printFull() {
        printSummary();

        var allSources = new HashSet<Source>();
        for (var section : loaded) {
            allSources.add(section.getSource());
        }
        for (var section : covered) {
            allSources.add(section.getSource());
        }

        var loadedLines = linesByFile(loaded);
        var coveredLines = linesByFile(covered);

        for (var source : allSources) {
            var path = source.getPath();
            if (path == null) {
                continue;
            }
            System.out.println(path);
            var loadedLinesOfSource = loadedLines.getOrDefault(source, new HashSet());
            var coveredLinesOfSource = coveredLines.getOrDefault(source, new HashSet());
            for (int i = 1; i <= source.getLineCount(); i++) {
                var c = ' ';
                if (loadedLinesOfSource.contains(i)) {
                    c = coveredLinesOfSource.contains(i) ? '+' : '-';
                }
                System.out.println(String.format("%s %s", c, source.getCharacters(i)));
            }
        }
    }

    public static Map<Source, Set<Integer>> linesByFile(Set<SourceSection> sections) {
        var out = new HashMap<Source, Set<Integer>>();
        for (var section : sections) {
            var source = section.getSource();
            out.putIfAbsent(source, new HashSet());
            var lines = out.get(source);
            for (int i = section.getStartLine(); i <= section.getEndLine(); i++) {
                lines.add(i);
            }
        }
        return out;
    }
}
