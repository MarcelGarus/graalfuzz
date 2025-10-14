package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.generator.Trace.Call;

public class Pool {

    public static class PoolEntry {

        public final Trace trace;
        public final Coverage coverage;
        public final double quality;

        public PoolEntry(Trace trace, Coverage coverage) {
            this.trace = trace;
            this.coverage = coverage;
            this.quality = coverage.getCovered().size() * 10.0 + trace.entries.size();
        }
    }

    private final List<PoolEntry> entries;
    private final Random random;

    public Pool() {
        this.entries = new ArrayList<>();
        this.random = new Random();
    }

    public void add(Trace trace, Coverage coverage) {
        entries.add(new PoolEntry(trace, coverage));
    }

    public Trace createNewTrace() {
        if (entries.isEmpty() || random.nextDouble() < 0.1) {
            var trace = new Trace();
            trace.add(new Call((new Universe()).generateValue(random)));
            return trace;
        }

        while (true) {
            var newTrace = selectWeightedEntry().trace.rethinkLastDecision(random);
            if (isWorthExploring(newTrace)) {
                return newTrace;
            }
        }
    }

    private PoolEntry selectWeightedEntry() {
        if (entries.size() == 1) {
            return entries.get(0);
        }

        // var maxQuality = entries.stream().mapToDouble(entry -> entry.quality).max().getAsDouble();
        // for (var entry : entries) {
        //     if (entry.quality == maxQuality) {
        //         return entry;
        //     }
        // }
        double totalQuality = entries.stream()
                .mapToDouble(entry -> entry.quality)
                .sum();

        if (totalQuality == 0.0) {
            return entries.get(random.nextInt(entries.size()));
        }

        double randomValue = random.nextDouble() * totalQuality;
        double cumulativeQuality = 0.0;

        for (var entry : entries) {
            cumulativeQuality += entry.quality;
            if (randomValue <= cumulativeQuality) {
                return entry;
            }
        }

        // Fallback to last entry (should not happen with proper math)
        return entries.get(entries.size() - 1);
    }

    private boolean isWorthExploring(Trace trace) {
        for (var entry : entries) {
            if (entry.trace.startsWith(trace) && entry.trace.numDecisions() == trace.numDecisions()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return entries.size();
    }

    public void printStats() {
        System.out.println("Pool stats: " + entries.size() + " entries");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            System.out.println("  Entry " + i + ": coverage=" + entry.coverage.getCovered().size()
                    + ", quality=" + entry.quality + ": " + entry.trace);
        }
    }
}
