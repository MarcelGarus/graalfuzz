package de.hpi.swa.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;

import de.hpi.swa.analysis.AnalysisEngine;
import de.hpi.swa.analysis.grouping.GroupingStrategy;
import de.hpi.swa.analysis.grouping.ResultGroup;
import de.hpi.swa.cli.logger.ConsoleLogger;
import de.hpi.swa.cli.logger.JsonLogger;
import de.hpi.swa.cli.logger.ResultLogger;
import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.coverage.CoverageInstrument;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner;

public class FuzzMain {

    public static void main(String[] args) {
        System.err.println("Welcome to the Fuzzer!");

        // Parse CLI options
        String language = "python";
        String code = null;
        String filePath = null;
        Boolean colorStdOut = true;
        Boolean tooling = false;
        Boolean group = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--language") || a.equals("-l")) {
                if (i + 1 < args.length)
                    language = args[++i];
            } else if (a.startsWith("--language=")) {
                language = a.substring("--language=".length());
            } else if (a.equals("--code") || a.equals("-c")) {
                if (i + 1 < args.length)
                    code = args[++i];
            } else if (a.startsWith("--code=")) {
                code = a.substring("--code=".length());
            } else if (a.equals("--file") || a.equals("-f")) {
                if (i + 1 < args.length)
                    filePath = args[++i];
            } else if (a.startsWith("--file=")) {
                filePath = a.substring("--file=".length());
            } else if (a.equals("--no-color")) {
                colorStdOut = false;
            } else if (a.equals("--tooling")) {
                tooling = true;
            } else if (a.equals("--group")) {
                group = true;
            }
        }

        var engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build();
        var context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
        var instrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

        // Display available languages
        System.err.print("Available languages:");
        for (var lang : context.getEngine().getLanguages().keySet()) {
            System.err.print(" " + lang);
        }
        System.err.println();

        if (instrument == null) {
            throw new IllegalStateException(
                    "CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
        }

        // Build source
        org.graalvm.polyglot.Source source;
        try {
            if (code != null) {
                System.err.println("Using inline " + language + " code from CLI");
                // Unescape newlines and tabs passed via CLI
                code = code.replace("\\n", "\n").replace("\\t", "\t");
                source = org.graalvm.polyglot.Source.newBuilder(language, code, "cli-inline").build();
            } else if (filePath != null) {
                var file = new File(filePath);
                System.err.println("Using " + language + " file: " + file.getPath());
                source = org.graalvm.polyglot.Source.newBuilder(language, file).build();
            } else {
                var pythonFile = new File("examples/program.py");
                System.err.println("Using default python program: " + pythonFile.getPath());
                source = org.graalvm.polyglot.Source.newBuilder("python", pythonFile).build();
            }
        } catch (IOException e) {
            System.err.println("Could not read file or build source.");
            e.printStackTrace();
            return;
        }

        System.err.println("Running program.\n");

        org.graalvm.polyglot.Value function;
        try {
            function = context.eval(source);
        } catch (PolyglotException e) {
            System.err.println("Error during execution:");
            FuzzMain.printException(e);
            return;
        }

        if (function.isNull()) {
            System.err.println("Returning because the code didn't evaluate to a function:");
            System.err.println(function);
            return;
        }

        // Output
        ResultLogger logger;
        if (tooling) {
            logger = new JsonLogger();
        } else {
            logger = new ConsoleLogger(colorStdOut);
        }

        // Fuzzing loop
        var pool = new Pool();
        var random = new Random();
        List<Runner.RunResult> allResults = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            var trace = pool.createNewTrace();
            instrument.coverage = new Coverage();
            var result = Runner.run(function, trace, random);
            var deduplicatedResult = result.withDeduplicatedTrace();

            // Add the entropy and its results to the pool for future selection
            pool.add(result.getTrace(), instrument.coverage);
            allResults.add(deduplicatedResult);

            logger.logRun(deduplicatedResult);
        }

        // Analysis
        GroupingStrategy groupingStrategy;
        if (group) {
            groupingStrategy = new GroupingStrategy.CompositeAllGroups();
        } else {
            groupingStrategy = new GroupingStrategy.NoGroups();
        }
        var analysis = new AnalysisEngine();
        List<ResultGroup> groups = analysis.analyze(allResults, pool, groupingStrategy);

        logger.logAnalysis(groups);
    }

    public static void printException(Exception e) {
        if (e instanceof PolyglotException) {
            runtimeError((PolyglotException) e);
        } else {
            System.err.println("Error: " + e.getMessage());
        }
    }

    static void runtimeError(PolyglotException error) {
        if (error.isSyntaxError()) {
            System.err.println(error.getMessage());
        } else if (error.isGuestException()) {
            org.graalvm.polyglot.SourceSection sourceLocation = error.getSourceLocation();
            if (sourceLocation != null) {
                System.err.println("[line " + sourceLocation.getStartLine() + "] " + "Error: " + error.getMessage());
            } else {
                System.err.println("Error: " + error.getMessage());
                error.printStackTrace();
            }
        } else {
            System.err.println(error.getMessage());
            error.printStackTrace();
        }
    }
}
