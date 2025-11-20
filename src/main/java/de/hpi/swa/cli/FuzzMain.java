package de.hpi.swa.cli;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;

import com.google.gson.Gson;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.coverage.CoverageInstrument;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Value;

public class FuzzMain {

    public static void main(String[] args) {
        System.err.println("Welcome to the Fuzzer!");

        // Parse CLI options
        String language = "python";
        String code = null;
        String filePath = null;
        Boolean colorStdOut = true;
        Boolean tooling = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--language") || a.equals("-l")) {
                if (i + 1 < args.length) language = args[++i];
            } else if (a.startsWith("--language=")) {
                language = a.substring("--language=".length());
            } else if (a.equals("--code") || a.equals("-c")) {
                if (i + 1 < args.length) code = args[++i];
            } else if (a.startsWith("--code=")) {
                code = a.substring("--code=".length());
            } else if (a.equals("--file") || a.equals("-f")) {
                if (i + 1 < args.length) filePath = args[++i];
            } else if (a.startsWith("--file=")) {
                filePath = a.substring("--file=".length());
            } else if (a.equals("--no-color")) {
                colorStdOut = false;
            } else if (a.equals("--tooling")) {
                tooling = true;
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
            throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
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

        // Fuzzing loop
        var pool = new Pool();
        var random = new Random();
        var gson = new Gson();
        
        for (int i = 0; i < 1000; i++) {
            if (!tooling) {
                System.out.print("New run. ");
            }

            var trace = pool.createNewTrace();
            instrument.coverage = new Coverage();
            var result = de.hpi.swa.generator.Runner.run(function, trace, random);
            var deduplicatedResult = result.withDeduplicatedTrace();

            if (tooling) {
                System.out.println(gson.toJson(deduplicatedResult));
            } else {
                System.out.print(String.format("%-20s", Value.format(deduplicatedResult.getInput(), deduplicatedResult.getUniverse())));
                System.out.println("  Trace: " + deduplicatedResult.getTrace().toString(colorStdOut));
            }

            // Add the entropy and its results to the pool for future selection
            pool.add(result.getTrace(), instrument.coverage);
        }
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
