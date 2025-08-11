package de.hpi.swa.generator;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Entropy {

    private List<Byte> data;
    private int cursor;
    private final Random random;

    public Entropy(long seed) {
        this.data = new ArrayList<>();
        this.cursor = 0;
        this.random = new Random(seed);
    }

    public byte nextByte() {
        if (cursor + 1 > data.size()) {
            data.add((byte) random.nextInt());
        }
        var b = data.get(cursor);
        cursor += 1;
        return b;
    }

    public byte nextByte(int max) {
        var val = nextByte() % max;
        if (val < 0) {
            val = -val;
        }
        var b = (byte) val;
        data.set(cursor - 1, b);
        return b;
    }

    public int nextInt() {
        var bytes = new byte[4];
        for (var i = 0; i < 4; i++) {
            bytes[i] = nextByte();
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    public boolean nextBoolean() {
        return nextByte() % 2 == 0;
    }

    public void reset() {
        this.cursor = 0;
    }

    public void mutate(double temperature) {
        if (temperature < 0.0 || temperature > 1.0) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 1.0");
        }
        int newSize = (int) (data.size() * (1.0 - temperature));
        this.data = new ArrayList<>(this.data.subList(0, newSize));
        if (cursor > data.size()) {
            cursor = data.size();
        }
    }

    public int nextInt(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive");
        }
        return (nextInt() & 0x7fffffff) % max;
    }

    public void printSummary() {
        System.out.print("Entropy:");
        for (var b : data) {
            System.out.print(" " + b);
        }
        System.out.println();
    }
}
