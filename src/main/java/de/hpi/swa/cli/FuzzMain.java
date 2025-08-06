package de.hpi.swa.cli;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import de.hpi.swa.coverage.Coverage;
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
        var coverageInstrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

        if (coverageInstrument == null) {
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
        String jsProgram = """
            function sum(list) {
                var sum = 0;
                for (item of list) {
                    sum += item;
                }
                return sum;
            }

            function greet(amazingness) {
                if (amazingness > 10) {
                    console.log("Hello, very amazing world! " + amazingness);
                } else {
                    console.log("Hello, world!" + amazingness);
                }
            }

            var amazingness = sum([1, 2, 3]);
            greet(amazingness);
            greet
            """;

        var source = org.graalvm.polyglot.Source.newBuilder("js", jsProgram, "<string-input>").buildLiteral();

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

        for (var entry : coverageInstrument.getCoverageMap().entrySet()) {
            // entry.getValue().printResult(entry.getKey());
            var src = entry.getKey();
            var cover = entry.getValue();
            cover.printResult(src);
            // System.out.println("Coverage of " + src.getPath() + " is " + Coverage.lineNumbers(cover.covered).size() + " / " + Coverage.lineNumbers(cover.loaded).size());
        }
        if (function.isNull()) {
            System.out.println("Returning because the code didn't evaluate to a function.");
            return;
        }

        // function.execute(Value.asValue(42));
        // var coverage = coverageInstrument.getCoverageMap();
        // System.out.println("Coverage: " + coverage);
        // coverage.printSummary();
        // var source = org.graalvm.polyglot.Source.newBuilder("js", jsProgram, "<string-input>").buildLiteral();
        // var function = polyglot.eval(source);
        // System.out.println(function);
        // System.out.println(function.isMetaObject());
        // function.execute(Value.asValue(42));
        var generator = new Generator();
        for (int i = 0; i < 10; i++) {
            function.execute(generator.generateValue());
            // coverageInstrument.getCoverageMap();
            var coverage = coverageInstrument.getCoverageMap();
            // System.out.println("Coverage: " + coverage);
            for (var entry : coverageInstrument.getCoverageMap().entrySet()) {
                // entry.getValue().printResult(entry.getKey());
                var src = entry.getKey();
                var cover = entry.getValue();
                System.out.println("Coverage of " + src.getPath() + " is " + Coverage.lineNumbers(cover.covered).size() + " / " + Coverage.lineNumbers(cover.loaded).size());
            }
        }
    }

    // public static Map<com.oracle.truffle.api.source.Source, Coverage> runToFindCoverage(String loxCode) {
    //     try (Engine engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build(); Context context = Context.newBuilder("lox").engine(engine).allowAllAccess(true).build()) {
    //         CoverageInstrument coverageInstrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);
    //         if (coverageInstrument == null) {
    //             throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
    //         }
    //         var source = org.graalvm.polyglot.Source.newBuilder("lox", loxCode, "<string-input>").buildLiteral();
    //         try {
    //             var value = context.eval(source);
    //             System.err.println("Returned: " + value);
    //         } catch (PolyglotException e) {
    //             System.err.println("Error during Lox execution:");
    //             FuzzMain.printException(e);
    //         }
    //         return coverageInstrument.getCoverageMap();
    //     } catch (Exception e) {
    //         throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
    //     }
    // }
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
