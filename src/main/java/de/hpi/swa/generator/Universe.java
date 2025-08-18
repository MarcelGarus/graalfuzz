package de.hpi.swa.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.generator.TraceTree.Event;

public class Universe {

    public Universe(Entropy entropy, Coverage coverage, TraceTree tree) {
        this.entropy = entropy;
        this.coverage = coverage;
        this.tree = tree;
    }

    public TraceTree tree;
    public Coverage coverage;
    public Entropy entropy;
    private int nextId = 0;

    public int reserveId() {
        var id = nextId;
        nextId++;
        return id;
    }

    public void run(Value function, Complexity complexity) {
        var input = generateValue(complexity);
        if (input instanceof QuantumObject quantumObject) {
            quantumObject.members.put("org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments", false);
            quantumObject.members.put("org.graalvm.python.embedding.PositionalArguments.is_positional_arguments", false);
        }
        System.out.println("Running with " + valueToString(input));
        tree = tree.add(new Event.Run(valueToString(input)));
        try {
            var returnValue = function.execute(Value.asValue(input));
            System.out.println("Returned: " + returnValue);
            tree.add(new Event.Return(returnValue.toString(), coverage));
        } catch (PolyglotException e) {
            System.out.println("Crashed: " + e.getMessage());
            tree.add(new Event.Crash(e.getMessage(), coverage));
        }
    }

    static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return value.toString();
    }

    public Object generateValue(Complexity complexity) {
        return switch (entropy.nextByte(6)) {
            case 0 ->
                null;
            case 1 ->
                entropy.nextBoolean();
            case 2 ->
                entropy.nextInt(100);
            case 3 ->
                (double) entropy.nextInt(100);
            case 4 ->
                generateString(complexity);
            case 5 ->
                new QuantumObject();
            default ->
                throw new IllegalStateException("unreachable");
        };
    }

    private String generateString(Complexity complexity) {
        var length = complexity.generateInt(entropy);
        var sb = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            sb.append((char) ('a' + entropy.nextInt(26)));
        }
        return sb.toString();
    }

    class QuantumObject implements ProxyObject { // ProxyArray, ProxyExecutable

        public final int id;
        public final Map<String, Object> members = new HashMap<>();
        public final Set<String> nonMembers = new HashSet<>();

        public QuantumObject() {
            this.id = reserveId();
        }

        @Override
        public boolean hasMember(String key) {
            if (members.containsKey(key)) {
                return true;
            }
            if (nonMembers.contains(key)) {
                return false;
            }
            var hasMember = entropy.nextBoolean();
            if (hasMember) {
                var member = generateValue(new Complexity(10));
                tree = tree.add(new Event.DecideMemberExists(id, key, valueToString(member)));
                members.put(key, member);
            } else {
                tree = tree.add(new Event.DecideMemberDoesNotExist(id, key));
                nonMembers.add(key);
            }
            return hasMember;
        }

        @Override
        public Object getMember(String key) {
            return members.get(key);
        }

        @Override
        public void putMember(String key, Value value) {
            members.put(key, value);
        }

        @Override
        public Object getMemberKeys() {
            return new String[0];
        }

        @Override
        public String toString() {
            return "object_" + id;
        }
    }
}
