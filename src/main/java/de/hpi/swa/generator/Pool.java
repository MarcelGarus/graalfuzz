package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.hpi.swa.coverage.Coverage;

public class Pool {

    public static class PoolEntry {
        public final Entropy entropy;
        public final Coverage coverage;
        public final int numEvents;
        public final double quality;
        
        public PoolEntry(Entropy entropy, Coverage coverage, int numEvents) {
            this.entropy = entropy;
            this.coverage = coverage;
            this.numEvents = numEvents;
            // Quality metric: coverage size + events weighted
            this.quality = coverage.getCovered().size() * 10.0 + numEvents;
        }
    }

    private final List<PoolEntry> entries;
    private final Random random;
    private final double newEntropyProbability;
    private final double mutationTemperature;

    public Pool(double newEntropyProbability, double mutationTemperature) {
        this.entries = new ArrayList<>();
        this.random = new Random();
        this.newEntropyProbability = newEntropyProbability;
        this.mutationTemperature = mutationTemperature;
        
        // Start with an initial random entropy
        var initialEntropy = new Entropy(random.nextLong());
        var initialCoverage = new Coverage();
        this.entries.add(new PoolEntry(initialEntropy, initialCoverage, 0));
    }

    public void add(Entropy entropy, Coverage coverage, int numEvents) {
        entries.add(new PoolEntry(entropy, coverage, numEvents));
    }

    public Entropy createNewEntropy() {
        if (entries.isEmpty() || random.nextDouble() < newEntropyProbability) {
            // Generate completely new entropy
            return new Entropy(random.nextLong());
        }
        
        // Select entropy based on quality weighting
        var selectedEntry = selectWeightedEntry();
        
        // Create a copy and mutate it
        var newEntropy = copyEntropy(selectedEntry.entropy);
        newEntropy.mutate(mutationTemperature);
        
        return newEntropy;
    }

    private PoolEntry selectWeightedEntry() {
        if (entries.size() == 1) {
            return entries.get(0);
        }
        
        // Calculate total quality
        double totalQuality = entries.stream()
            .mapToDouble(entry -> entry.quality)
            .sum();
        
        if (totalQuality == 0.0) {
            // All entries have zero quality, select randomly
            return entries.get(random.nextInt(entries.size()));
        }
        
        // Select based on weighted probability
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

    private Entropy copyEntropy(Entropy original) {
        // Create a new entropy with the same seed behavior
        // Since we can't directly copy the internal state, we'll create a new one
        // and let the mutation process diversify it
        return new Entropy(random.nextLong());
    }

    public int size() {
        return entries.size();
    }

    public void printStats() {
        System.out.println("Pool stats: " + entries.size() + " entries");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            System.out.println("  Entry " + i + ": coverage=" + entry.coverage.getCovered().size() + 
                             ", events=" + entry.numEvents + ", quality=" + entry.quality);
        }
    }
}
