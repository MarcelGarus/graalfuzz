package de.hpi.swa.cli;

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.coverage.CoverageInstrument;

public class FuzzMain {

    public static void main(String[] args) {
        System.out.println("Hello, FuzzMain!");

        var polyglot = Context.newBuilder().build();

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

        System.out.println("Running Lox program:\n" + loxProgram);

        var coverage = runToFindCoverage(loxProgram);
        // coverage.printSummary();
        System.out.println("Coverage: " + coverage);

        // var source = org.graalvm.polyglot.Source.newBuilder("js", "2 + 3", "<string-input>").buildLiteral();
        // var value = polyglot.eval(source);
        // System.out.println(value);
    }

    public static Map<com.oracle.truffle.api.source.Source, Coverage> runToFindCoverage(String loxCode) {
        try (Engine engine = Engine.newBuilder().option(CoverageInstrument.ID, "true").build(); Context context = Context.newBuilder("lox").engine(engine).allowAllAccess(true).build()) {
            CoverageInstrument coverageInstrument = engine.getInstruments().get(CoverageInstrument.ID).lookup(CoverageInstrument.class);

            if (coverageInstrument == null) {
                throw new IllegalStateException("CoverageInstrument not found. Ensure it's on the classpath and correctly registered.");
            }

            var source = org.graalvm.polyglot.Source.newBuilder("lox", loxCode, "<string-input>").buildLiteral();

            try {
                var value = context.eval(source);
                System.err.println("Returned: " + value);
            } catch (PolyglotException e) {
                System.err.println("Error during Lox execution:");
                FuzzMain.printException(e);
            }

            return coverageInstrument.getCoverageMap();
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
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
