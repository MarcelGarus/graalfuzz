package de.hpi.swa.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

public class QuantumObject implements ProxyObject, ProxyArray, ProxyExecutable {

    private final Universe universe;
    public final int id;
    private final Map<String, Object> members = new HashMap<>();
    private final Set<String> nonMembers = new HashSet<>();
    private final Map<Long, Object> elements = new HashMap<>();

    public QuantumObject(Universe universe) {
        this.universe = universe;
        this.id = universe.reserveId();
    }

    @Override
    public boolean hasMember(String key) {
        System.out.println("Asked: " + id + ".hasMember(\"" + key + "\")");
        universe.currentNode = universe.currentNode.hasMember(String.valueOf(id), key);
        if (members.containsKey(key)) {
            universe.currentNode = universe.currentNode.answer("true");
            return true;
        }
        if (nonMembers.contains(key)) {
            universe.currentNode = universe.currentNode.answer("false");
            return false;
        }
        if (universe.random.nextBoolean()) {
            members.put(key, new QuantumObject(universe));
            universe.currentNode = universe.currentNode.answer("true");
            return true;
        } else {
            nonMembers.add(key);
            universe.currentNode = universe.currentNode.answer("false");
            return false;
        }
    }

    @Override
    public Object getMember(String key) {
        universe.currentNode = universe.currentNode.getMember(String.valueOf(id), key);
        Object member = members.get(key);
        universe.currentNode = universe.currentNode.answer(member == null ? "null" : "object");
        return member;
    }

    @Override
    public void putMember(String key, Value value) {
        // TODO: How to represent value in the tree?
        universe.currentNode = universe.currentNode.putMember(String.valueOf(id), key, value.toString());
        members.put(key, value);
    }

    @Override
    public Object getMemberKeys() {
        universe.currentNode = universe.currentNode.getMemberKeys(String.valueOf(id));
        // The returned array is not influencing the tree for now.
        return new String[0];
    }

    @Override
    public Object get(long index) {
        universe.currentNode = universe.currentNode.get(String.valueOf(id), index);
        Object element = elements.get(index);
        universe.currentNode = universe.currentNode.answer(element == null ? "null" : "object");
        return element;
    }

    @Override
    public void set(long index, Value value) {
        universe.currentNode = universe.currentNode.set(String.valueOf(id), index, value.toString());
        elements.put(index, value);
    }

    @Override
    public long getSize() {
        universe.currentNode = universe.currentNode.getSize(String.valueOf(id));
        long size = elements.size();
        universe.currentNode = universe.currentNode.answer(String.valueOf(size));
        return size;
    }

    @Override
    public Object execute(Value... arguments) {
        universe.currentNode = universe.currentNode.execute(String.valueOf(id), arguments.length);
        Object result = new QuantumObject(universe);
        universe.currentNode = universe.currentNode.answer("object");
        return result;
    }
}
