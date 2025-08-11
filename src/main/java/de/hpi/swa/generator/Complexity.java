package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.Collections;

public record Complexity(int value) {

    public Complexity {
        if (value < 0) {
            throw new IllegalArgumentException("Complexity value must be non-negative.");
        }
    }

    public int generateInt(Entropy entropy) {
        if (value == 0) {
            return 0;
        }
        return entropy.nextInt(value);
    }

    public Complexity[] split(int n, Entropy entropy) {
        if (n <= 0) {
            return new Complexity[0];
        }
        if (value == 0) {
            var result = new Complexity[n];
            for (int i = 0; i < n; i++) {
                result[i] = new Complexity(0);
            }
            return result;
        }

        var splits = new ArrayList<Integer>(n - 1);
        for (int i = 0; i < n - 1; i++) {
            splits.add(entropy.nextInt(value + 1));
        }
        Collections.sort(splits);

        var complexities = new Complexity[n];
        int lastSplit = 0;
        for (int i = 0; i < n; i++) {
            var split = splits.get(i);
            complexities[i] = new Complexity(split - lastSplit);
            lastSplit = split;
        }
        // Shuffle.
        for (int i = n; i > 1; i--) {
            swap(complexities, i - 1, entropy.nextInt(i));
        }
        return complexities;
    }

    private static void swap(Object[] arr, int i, int j) {
        Object tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    public Complexity[] split(Entropy entropy) {
        var n = generateInt(entropy);
        return split(n, entropy);
    }
}
