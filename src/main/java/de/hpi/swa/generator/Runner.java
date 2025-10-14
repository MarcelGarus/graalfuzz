package de.hpi.swa.generator;

import java.util.Random;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.proxy.ProxyObject;

import de.hpi.swa.generator.Trace.Call;
import de.hpi.swa.generator.Trace.Crash;
import de.hpi.swa.generator.Trace.Member;
import de.hpi.swa.generator.Trace.QueryMember;
import de.hpi.swa.generator.Trace.Return;

public abstract class Runner {

    public static Trace runWithRandomArgs(org.graalvm.polyglot.Value function, Random random) {
        var universe = new Universe();
        var input = universe.generateValue(random);
        var trace = new Trace();
        run(function, universe, input, trace, random);
        return trace;
    }

    public static RunResult run(org.graalvm.polyglot.Value function, Trace startingWith, Random random) {
        var universe = startingWith.toUniverse();
        var input = ((Call) startingWith.entries.get(0)).arg();
        var trace = new Trace();
        run(function, universe, input, trace, random);
        return new RunResult(universe, input, trace);
    }

    public record RunResult(Universe universe, Value input, Trace trace) {

        public Universe getUniverse() {
            return universe;
        }

        public Value getInput() {
            return input;
        }

        public Trace getTrace() {
            return trace;
        }
    }

    private static void run(org.graalvm.polyglot.Value function, Universe universe, Value input, Trace trace, Random random) {
        trace.add(new Call(input));
        try {
            var polyglotInput = toPolyglotValue(input, universe, trace, random);
            var returnValue = function.execute(polyglotInput);
            trace.add(new Return(returnValue.toString()));
        } catch (PolyglotException e) {
            trace.add(new Crash(e.getMessage()));
        }
    }

    public static org.graalvm.polyglot.Value toPolyglotValue(Value value, Universe universe, Trace trace, Random random) {
        return switch (value) {
            case Value.Null() ->
                org.graalvm.polyglot.Value.asValue(null);
            case Value.Boolean(var bool) ->
                org.graalvm.polyglot.Value.asValue(bool);
            case Value.Int(var int_) ->
                org.graalvm.polyglot.Value.asValue(int_);
            case Value.Double(var double_) ->
                org.graalvm.polyglot.Value.asValue(double_);
            case Value.StringValue(var string) ->
                org.graalvm.polyglot.Value.asValue(string);
            case Value.ObjectValue(var id) -> {
                var quantumObject = universe.getOrCreateObject(id);
                yield org.graalvm.polyglot.Value.asValue(new ProxyObject() {
                    @Override
                    public boolean hasMember(String key) {
                        if (key.equals("org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments")
                                || key.equals("org.graalvm.python.embedding.PositionalArguments.is_positional_arguments")) {
                            return false;
                        }
                        trace.add(new QueryMember(id, key));
                        if (quantumObject.members.containsKey(key)) {
                            var member = quantumObject.members.get(key);
                            trace.add(new Member(id, key, member));
                            return member != null;
                        }
                        var hasMember = random.nextBoolean();
                        var value = hasMember ? universe.generateValue(random) : null;
                        quantumObject.members.put(key, value);
                        trace.add(new Member(id, key, value));
                        return hasMember;
                    }

                    @Override
                    public Object getMember(String key) {
                        return toPolyglotValue(quantumObject.members.get(key), universe, trace, random);
                    }

                    @Override
                    public void putMember(String key, org.graalvm.polyglot.Value value) {
                        throw new IllegalAccessError("Can't set members.");
                    }

                    @Override
                    public Object getMemberKeys() {
                        throw new IllegalAccessError("Can't get all member keys.");
                    }

                    @Override
                    public String toString() {
                        return id.toString();
                    }
                });
            }
        };
    }
}
