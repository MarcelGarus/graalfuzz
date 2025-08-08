package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public record Complexity(int value) {

    public Complexity {
        if (value < 0) {
            throw new IllegalArgumentException("Complexity value must be non-negative.");
        }
    }

    public int generateInt(Random random) {
        if (value == 0) {
            return 0;
        }
        return random.nextInt(value);
    }

    public List<Complexity> split(int n, Random random) {
        if (n <= 0) {
            return Collections.emptyList();
        }
        if (value == 0) {
            var result = new ArrayList<Complexity>(n);
            for (int i = 0; i < n; i++) {
                result.add(new Complexity(0));
            }
            return result;
        }

        var splits = new ArrayList<Integer>(n - 1);
        for (int i = 0; i < n - 1; i++) {
            splits.add(random.nextInt(value + 1));
        }
        Collections.sort(splits);

        var complexities = new ArrayList<Complexity>(n);
        int lastSplit = 0;
        for (int split : splits) {
            complexities.add(new Complexity(split - lastSplit));
            lastSplit = split;
        }
        complexities.add(new Complexity(value - lastSplit));
        Collections.shuffle(complexities, random);
        return complexities;
    }

    public List<Complexity> split(Random random) {
        var n = generateInt(random);
        return split(n, random);
    }
}
