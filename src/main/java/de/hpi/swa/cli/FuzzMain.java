package de.hpi.swa.cli;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.coverage.CoverageInstrument;
import de.hpi.swa.generator.Complexity;
import de.hpi.swa.generator.Entropy;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.TraceTree;
import de.hpi.swa.generator.Universe;

public class FuzzMain {

    public static void main(String[] args) {
        System.out.println("Hello, FuzzMain!");

        var engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build();
        var context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
        var instrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

        if (instrument == null) {
            throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
        }

        org.graalvm.polyglot.Source source;
        try {
            File file = new File("examples/program.py");
            source = org.graalvm.polyglot.Source.newBuilder("python", file).build();
        } catch (IOException e) {
            System.err.println("Could not read file.");
            e.printStackTrace();
            return;
        }

        System.out.println("Running program.\n");

        Value function;
        try {
            function = context.eval(source);
        } catch (PolyglotException e) {
            System.err.println("Error during execution:");
            FuzzMain.printException(e);
            return;
        }

        System.err.println("The program returned this:");
        System.out.println(function);

        if (function.isNull()) {
            System.out.println("Returning because the code didn't evaluate to a function.");
            return;
        }

        var tree = new TraceTree();
        var pool = new Pool(0.1, 0.3); // 10% probability for new entropy, 30% mutation temperature
        
        for (int i = 0; i < 1000; i++) {
            var entropy = pool.createNewEntropy();
            var coverage = new Coverage();
            var universe = new Universe(entropy, coverage, tree);
            instrument.coverage = coverage;
            tree.visit();
            universe.run(function, new Complexity(10));
            System.out.println("Ran. Result: " + coverage + " and " + universe.numEvents + " events happened");
            entropy.printSummary();
            
            // Add the entropy and its results to the pool for future selection
            pool.add(entropy, coverage, universe.numEvents);
            
            // Print pool stats every 100 iterations
            if (i % 100 == 99) {
                pool.printStats();
            }
        }
        System.out.println(tree);
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
