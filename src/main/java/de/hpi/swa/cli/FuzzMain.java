package de.hpi.swa.cli;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.coverage.CoverageInstrument;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Value;

public class FuzzMain {

    public static void main(String[] args) {
        System.out.println("Welcome to the Fuzzer!");

        var engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build();
        var context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
        var instrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

        // Display available languages
        System.out.print("Available languages:");
        for (var lang : context.getEngine().getLanguages().keySet()) {
            System.out.print(" " + lang);
        }
        System.out.println();

        if (instrument == null) {
            throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
        }

        org.graalvm.polyglot.Source source;
        try {
            // // Smalltalk
            // File squeakFile = new File("examples/program.st");
            // System.out.println("Using TruffleSqueak (Smalltalk) program");
            // source = org.graalvm.polyglot.Source.newBuilder("smalltalk", squeakFile).build();

            // Fallback to Python
            File pythonFile = new File("examples/program.py");
            source = org.graalvm.polyglot.Source.newBuilder("python", pythonFile).build();
        } catch (IOException e) {
            System.err.println("Could not read file.");
            e.printStackTrace();
            return;
        }

        System.out.println("Running program.\n");

        org.graalvm.polyglot.Value function;
        try {
            function = context.eval(source);
        } catch (PolyglotException e) {
            System.err.println("Error during execution:");
            FuzzMain.printException(e);
            return;
        }

        if (function.isNull()) {
            System.out.println("Returning because the code didn't evaluate to a function:");
            System.out.println(function);
            return;
        }

        // var tree = new TraceTree();
        var pool = new Pool();
        var random = new Random();

        for (int i = 0; i < 1000; i++) {
            System.out.print("New run. ");
            var trace = pool.createNewTrace();
            instrument.coverage = new Coverage();
            var result = de.hpi.swa.generator.Runner.run(function, trace, random);
            System.out.print(String.format("%-20s", Value.format(result.getInput(), result.getUniverse())));
            System.out.println("  Trace: " + result.getTrace().deduplicate());

            // Add the entropy and its results to the pool for future selection
            pool.add(result.getTrace(), instrument.coverage);
        }
        // System.out.println(tree);
    }

    public static void printException(Exception e) {
        if (e instanceof PolyglotException error) {
            runtimeError(error);
        } else {
            System.err.println("Error: " + e.getMessage());
        }
    }

    static void runtimeError(PolyglotException error) {
        if (error.isSyntaxError()) {
            System.err.println(error.getMessage());
        } else if (error.isGuestException()) {
            var sourceLocation = error.getSourceLocation();
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
