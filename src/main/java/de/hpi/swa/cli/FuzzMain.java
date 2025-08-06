package de.hpi.swa.cli;

import java.io.File;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import de.hpi.swa.coverage.CoverageInstrument;
import de.hpi.swa.generator.Generator;

// Notes:
// - existierenden Code (z.B. Tests) fuzzen, um Wertebereich einzuschränken
// - example mining definitiv sinnvoll als Future work
// - test amplification
// - anhand Benutzung von Objekten Klassen herausfinden
//   -> Execution-Kontext automatisch rausfinden, Abhängigkeiten/Imports rausfinden
//   -> mit Benni quatschen (z.B. "safe" JSON-Objekte von Flask)
public class FuzzMain {

    public static void main(String[] args) {
        System.out.println("Hello, FuzzMain!");

        var engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build();
        var context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
        var instrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

        if (instrument == null) {
            throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
        }

        // var file = Path.of(args[0]).toFile();
        // if (!file.isFile()) {
        //     System.err.println("Cannot access file " + arg);
        //     System.exit(1);
        // }
        String loxProgram = """
            // Simple Lox program for testing coverage
            var a = 10;
            if (a > 5) {
                print "a is greater than 5";
            } else {
                print "a is not greater than 5";
            }
            for (var i = 0; i < 2; i = i + 1) {
                print i;
            }
            """;
        org.graalvm.polyglot.Source source;
        try {
            File jsFile = new File("examples/program.js");
            source = org.graalvm.polyglot.Source.newBuilder("js", jsFile).build();
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

        var generator = new Generator();
        for (int i = 0; i < 10; i++) {
            instrument.coverage.clear();
            var input = generator.generateValue();
            System.out.print("Input: " + input);
            for (var j = input.toString().length(); j < 22; j++) {
                System.out.print(" ");
            }
            function.execute(input);
            instrument.coverage.printSummary();
        }
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
