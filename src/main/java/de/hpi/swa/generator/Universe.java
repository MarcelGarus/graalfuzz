package de.hpi.swa.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import de.hpi.swa.generator.TraceTree.Event;

public class Universe {

    public Universe(TraceTree tree, Random random) {
        this.random = random;
        this.tree = tree;
    }

    public TraceTree tree;
    public final Random random;
    private int nextId = 0;
    private boolean assumeDeterminism = true;

    public int reserveId() {
        var id = nextId;
        nextId++;
        return id;
    }

    public void run(Value function, Complexity complexity) {
        tree.numVisits++;

        var input = generateValue(complexity);
        if (input instanceof QuantumObject quantumObject) {
            quantumObject.members.put("org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments", false);
            quantumObject.members.put("org.graalvm.python.embedding.PositionalArguments.is_positional_arguments", false);
        }
        tree = tree.add(new Event.Run(valueToString(input)), false);
        try {
            var returnValue = function.execute(Value.asValue(input));
            System.out.println("Returned: " + returnValue);
            tree.add(new Event.Returns(returnValue.toString()), assumeDeterminism);
        } catch (PolyglotException e) {
            System.out.println("Crashed: " + e.getMessage());
            tree.add(new Event.Crash(e.getMessage()), assumeDeterminism);
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
        return switch (random.nextInt(6)) {
            case 0 ->
                null;
            case 1 ->
                random.nextBoolean();
            case 2 ->
                random.nextInt(100);
            case 3 ->
                random.nextDouble() * 100;
            case 4 ->
                generateString(complexity);
            case 5 ->
                new QuantumObject();
            // case 5 ->
            //     generateArray(complexity);
            default ->
                throw new IllegalStateException("unreachable");
        };
    }

    private String generateString(Complexity complexity) {
        var length = complexity.generateInt(random);
        var sb = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private Object[] generateArray(Complexity complexity) {
        var complexities = complexity.split(random);
        var array = new Object[complexities.size()];
        for (var i = 0; i < complexities.size(); i++) {
            array[i] = generateValue(complexities.get(i));
        }
        return array;
    }

    class QuantumObject implements ProxyObject { // ProxyArray, ProxyExecutable

        public final int id;
        public final Map<String, Object> members = new HashMap<>();
        public final Set<String> nonMembers = new HashSet<>();
        // private final Map<Long, Object> elements = new HashMap<>();

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
            var hasMember = random.nextBoolean();
            if (hasMember) {
                var member = generateValue(new Complexity(10));
                tree = tree.add(new Event.DecideMemberExists(id, key, valueToString(member)), false);
                members.put(key, member);
            } else {
                tree = tree.add(new Event.DecideMemberDoesNotExist(id, key), false);
                nonMembers.add(key);
            }
            return hasMember;
        }

        @Override
        public Object getMember(String key) {
            return 5;
            // return members.get(key);
        }

        @Override
        public void putMember(String key, Value value) {
            members.put(key, value);
        }

        @Override
        public Object getMemberKeys() {
            // tree = tree.makeDecision(new Decision.GetMemberKeys(id));
            // The returned array is not influencing the tree for now.
            return new String[0];
        }

        // @Override
        // public Object get(long index) {
        //     tree = tree.makeDecision(new Decision.Get(id, index));
        //     Object element = elements.get(index);
        //     tree = tree.makeDecision(new Decision.Answer(element == null ? "null" : "object"));
        //     return element;
        // }
        // @Override
        // public void set(long index, Value value) {
        //     tree = tree.makeDecision(new Decision.Set(id, index, value));
        //     elements.put(index, value);
        // }
        // @Override
        // public long getSize() {
        //     tree = tree.makeDecision(new Decision.GetSize(id));
        //     long size = elements.size();
        //     tree = tree.makeDecision(new Decision.Answer(String.valueOf(size)));
        //     return size;
        // }
        // @Override
        // public Object execute(Value... arguments) {
        //     tree = tree.makeDecision(new Decision.Execute(id, arguments.length));
        //     Object result = new QuantumObject();
        //     tree = tree.makeDecision(new Decision.Answer("object"));
        //     return result;
        // }
        @Override
        public String toString() {
            return "object_" + id;
        }
    }
}
