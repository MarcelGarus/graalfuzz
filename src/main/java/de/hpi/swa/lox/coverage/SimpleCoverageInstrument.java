package de.hpi.swa.lox.instrument;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A simple TruffleInstrument to collect code coverage information. It tracks
 * all executed source sections tagged with StatementTag. The coverage report is
 * written to a file when the instrument is disposed.
 */
@TruffleInstrument.Registration(id = SimpleCoverageInstrument.ID, name = "Lox Coverage Instrument", version = "0.1")
public class SimpleCoverageInstrument extends TruffleInstrument {

    public static final String ID = "lox-coverage"; // Unique ID for your instrument

    // Set to store unique SourceSections that have been executed.
    // ConcurrentHashMap is used to ensure thread-safety if multiple guest language threads
    // are executing concurrently.
    private final Set<SourceSection> executedSourceSections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Env env;
    private EventBinding<?> binding;
    private Path outputFile;

    @Override
    protected void onCreate(Env env) {
        this.env = env;
        // Retrieve the output file path from instrument options.
        // If not provided via command-line, it defaults to "lox_coverage_report.txt".
        String outputFilePath = env.getOptions().get("outputFile");
        if (outputFilePath == null) {
            outputFilePath = "lox_coverage_report.txt"; // Default output file
        }
        this.outputFile = Paths.get(outputFilePath);

        // Create a filter to only instrument nodes tagged as statements.
        // This focuses coverage on executable lines.
        SourceSectionFilter filter = SourceSectionFilter.newBuilder()
                .tag(StandardTags.StatementTag.class)
                .build();

        // Attach an ExecutionEventNodeFactory. This factory will create an ExecutionEventNode
        // for each matched source section.
        this.binding = env.getInstrumenter().attachExecutionEventFactory(filter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                // For each executed statement, add its source section to our set.
                // onReturnValue is called after the instrumented node successfully executes.
                return new ExecutionEventNode() {
                    @Override
                    protected void onReturnValue(EventContext context, Object result) {
                        executedSourceSections.add(context.getInstrumentedSourceSection());
                    }
                };
            }
        });

        // Register the instrument itself as a service. This allows other components
        // (though not used in this example) to look up and interact with this instrument.
        env.registerService(this);
    }

    @Override
    protected void onDispose(Env env) {
        // Detach the event binding to stop further instrumentation.
        if (binding != null) {
            binding.dispose();
        }

        // Write the collected coverage report to the file.
        // This is called when the PolyglotEngine is closed.
        writeCoverageReport();
    }

    /**
     * Writes the collected coverage information to the specified output file.
     * The report includes the total count of unique executed source sections
     * and a list of each executed source section's file, start line, and end
     * line.
     */
    private void writeCoverageReport() {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            writer.println("--- Lox Coverage Report ---");
            writer.println("Total unique executed source sections: " + executedSourceSections.size());
            writer.println("Executed Source Sections:");
            // Iterate through the collected source sections and print their details.
            for (SourceSection ss : executedSourceSections) {
                // Ensure source and line information is available before printing.
                if (ss.getSource() != null && ss.hasLineDetails()) { // Changed ss.has to ss.hasLineDetails()
                    writer.printf("  %s:%d-%d%n", ss.getSource().getName(), ss.getStartLine(), ss.getEndLine());
                } else {
                    writer.printf("  [Unknown Source Section]%n");
                }
            }
            writer.println("---------------------------");
            System.out.println("Lox coverage report written to: " + outputFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing coverage report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Defines the command-line options supported by this instrument. In this
     * case, it's just the 'outputFile' option. The option name is prefixed with
     * the instrument ID (e.g., 'lox-coverage.outputFile').
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.create(new OptionDescriptor[]{
            OptionDescriptor.newBuilder(
            new OptionKey<>("outputFile", String.class, "Path to the coverage output file."),
            SimpleCoverageInstrument.ID + ".outputFile" // Full option name: lox-coverage.outputFile
            ).build()
        });
    }

    /**
     * Returns the set of executed source sections. This method could be used by
     * other components to programmatically access coverage data before the
     * instrument is disposed.
     */
    public Set<SourceSection> getExecutedSourceSections() {
        return executedSourceSections;
    }
}
