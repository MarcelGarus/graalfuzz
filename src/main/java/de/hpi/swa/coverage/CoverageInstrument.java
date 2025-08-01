package de.hpi.swa.coverage;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

// Example for version of an expression coverage instrument.
//
// The instrument {@link #coverageMap keeps track} of all loaded
// {@link SourceSection}s and all coverd (i.e. executed) {@link SourceSection}s
// for each {@link Source}. At the end of the execution this information can be
// used to calculate coverage.
//
// The instrument is registered with the Truffle framework using the
// {@link Registration} annotation. The annotation specifies a unique
// {@link Registration#id}, a human readable {@link Registration#name} and
// {@link Registration#version} for the instrument. It also specifies all
// service classes that the instrument exports to other instruments and,
// exceptionally, tests. In this case the instrument itself is exported as a
// service and used in the CoverageInstrumentTest.
//
// NOTE: Fot the registration annotation to work the truffle dsl processor must
// be used (i.e. Must be a dependency. This is so in this maven project, as can
// be seen in the pom file.
@Registration(id = CoverageInstrument.ID, name = " Code Coverage", version = "0.1", services = CoverageInstrument.class)
public final class CoverageInstrument extends TruffleInstrument {

    @Option(name = "", help = "Enable  Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    public static final String ID = "code-coverage";

    final Map<Source, Coverage> coverageMap = new HashMap<>();

    public synchronized Map<Source, Coverage> getCoverageMap() {
        return Collections.unmodifiableMap(coverageMap);
    }

    @Override
    protected void onCreate(final Env env) {
        final OptionValues options = env.getOptions();
        if (ENABLED.getValue(options)) {
            enable(env);
            env.registerService(this);
        }
    }

    private void enable(final Env env) {
        System.out.println("Enabling instrument");
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().includeInternal(true).build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachLoadSourceSectionListener(filter, new GatherSourceSectionsListener(this), true);
        instrumenter.attachExecutionEventFactory(filter, new CoverageEventFactory(this));
    }

    @Override
    protected void onFinalize(Env env) {
        printResults(env);
    }

    private synchronized void printResults(final Env env) {
        final PrintStream printStream = new PrintStream(env.out());
        for (Source source : coverageMap.keySet()) {
            printResult(printStream, source);
        }
    }

    private void printResult(PrintStream printStream, Source source) {
        String path = source.getPath();

        Set<Integer> nonCoveredLineNumbers = nonCoveredLineNumbers(source);
        Set<Integer> loadedLineNumbers = coverageMap.get(source).loadedLineNumbers();
        // int numNonCovered = nonCoveredLineNumbers.
        // Set<Integer> coveredLineNumbers = Set loadedLineNumbers. .difference(nonCoveredLineNumbers);
        double coveredPercentage = 100 * ((double) loadedLineNumbers.size() - nonCoveredLineNumbers.size()) / ((double) loadedLineNumbers.size());
        printStream.println("==");
        printStream.println("Coverage of " + path + " is " + String.format("%.2f%%", coveredPercentage));
        printStream.println("loaded: " + loadedLineNumbers.size());
        printStream.println("non-covered: " + nonCoveredLineNumbers.size());
        // printStream.println("covered: " + coveredLineNumbers.size());
        for (int i = 1; i <= source.getLineCount(); i++) {
            char covered = getCoverageCharacter(nonCoveredLineNumbers, loadedLineNumbers, i);
            printStream.println(String.format("%s %s", covered, source.getCharacters(i)));
        }
    }

    private static char getCoverageCharacter(Set<Integer> nonCoveredLineNumbers, Set<Integer> loadedLineNumbers, int i) {
        if (loadedLineNumbers.contains(i)) {
            return nonCoveredLineNumbers.contains(i) ? '-' : '+';
        } else {
            return ' ';
        }
    }

    public synchronized Set<Integer> nonCoveredLineNumbers(final Source source) {
        return coverageMap.get(source).nonCoveredLineNumbers();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CoverageInstrumentOptionDescriptors();
    }

    synchronized void addLoaded(SourceSection sourceSection) {
        final Coverage coverage = coverageMap.computeIfAbsent(sourceSection.getSource(), (Source s) -> new Coverage());
        coverage.loaded.add(sourceSection);
    }

    synchronized void addCovered(SourceSection sourceSection) {
        final Coverage coverage = coverageMap.get(sourceSection.getSource());
        coverage.covered.add(sourceSection);
    }
}
