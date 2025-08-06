package de.hpi.swa.coverage;

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class Coverage {

    public final Set<SourceSection> loaded = new HashSet<>();
    public final Set<SourceSection> covered = new HashSet<>();

    public Set<SourceSection> nonCoveredSections() {
        final HashSet<SourceSection> nonCovered = new HashSet<>();
        nonCovered.addAll(loaded);
        nonCovered.removeAll(covered);
        return nonCovered;
    }

    public static Set<Integer> lineNumbers(Set<SourceSection> sections) {
        Set<Integer> lines = new HashSet<>();
        for (SourceSection ss : sections) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                lines.add(i);
            }
        }
        return lines;
    }

    public void printResult(Source source) {
        var path = source.getPath();
        var nonCoveredLineNumbers = lineNumbers(nonCoveredSections());
        var loadedLineNumbers = lineNumbers(loaded);
        var coveredPercentage = 100 * ((double) loadedLineNumbers.size() - nonCoveredLineNumbers.size()) / ((double) loadedLineNumbers.size());
        // System.out.println("==");
        // System.out.println("Coverage of " + path + " is " + lineNumbers(covered).size() + " / " + loadedLineNumbers.size());
        System.out.println("Coverage of " + path + " is " + String.format("%.2f%%", coveredPercentage));
        // System.out.println("loaded: " + loadedLineNumbers.size());
        // System.out.println("non-covered: " + nonCoveredLineNumbers.size());
        // System.out.println("covered: " + coveredLineNumbers.size());
        for (int i = 1; i <= source.getLineCount(); i++) {
            var c = getCoverageCharacter(nonCoveredLineNumbers, loadedLineNumbers, i);
            System.out.println(String.format("%s %s", c, source.getCharacters(i)));
        }
    }

    private static char getCoverageCharacter(Set<Integer> nonCoveredLineNumbers, Set<Integer> loadedLineNumbers, int i) {
        if (loadedLineNumbers.contains(i)) {
            return nonCoveredLineNumbers.contains(i) ? '-' : '+';
        } else {
            return ' ';
        }
    }
}
