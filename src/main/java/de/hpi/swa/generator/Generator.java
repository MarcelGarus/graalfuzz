package de.hpi.swa.generator;

import org.graalvm.polyglot.Value;

public final class Generator {

    public Value generateValue() {
        var number = Math.random() * 100;
        return Value.asValue(number);
    }
}
