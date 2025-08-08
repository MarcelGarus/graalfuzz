package de.hpi.swa.generator;

import java.util.HashMap;
import java.util.Random;

import org.graalvm.polyglot.Value;

public final class Generator {

    public Value generateValue(Random random, Complexity complexity) {
        return switch (random.nextInt(7)) {
            case 0 ->
                Value.asValue(random.nextDouble() * 100);
            case 1 ->
                Value.asValue(random.nextInt(100));
            case 2 ->
                Value.asValue(random.nextBoolean());
            case 3 ->
                Value.asValue(generateString(random, complexity));
            case 4 ->
                Value.asValue(null);
            case 5 ->
                generateArray(random, complexity);
            case 6 ->
                generateObject(random, complexity);
            default ->
                throw new IllegalStateException("unreachable");
        };
    }

    private String generateString(Random random, Complexity complexity) {
        var length = complexity.generateInt(random);
        var sb = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private Value generateArray(Random random, Complexity complexity) {
        var complexities = complexity.split(random);
        var array = new Object[complexities.size()];
        for (var i = 0; i < complexities.size(); i++) {
            array[i] = generateValue(random, complexities.get(i));
        }
        return Value.asValue(array);
    }

    private Value generateObject(Random random, Complexity complexity) {
        var complexities = complexity.split(random);
        var map = new HashMap<String, Object>(complexities.size());
        for (var i = 0; i < complexities.size(); i++) {
            map.put(generateString(random, complexities.get(i)), generateValue(random, complexities.get(i)));
        }
        return Value.asValue(map);
    }
}
