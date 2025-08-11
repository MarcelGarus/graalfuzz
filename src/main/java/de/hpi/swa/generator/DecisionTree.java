package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionTree {

    private final String content;
    private final List<DecisionTree> children;

    public DecisionTree() {
        this.content = "root";
        this.children = new ArrayList<>();
    }

    public DecisionTree(String content) {
        this.content = content;
        this.children = new ArrayList<>();
    }

    public void addChild(DecisionTree child) {
        children.add(child);
    }

    public List<DecisionTree> getChildren() {
        return children;
    }

    public String getContent() {
        return content;
    }

    private DecisionTree findOrCreateChild(String content) {
        for (DecisionTree child : children) {
            if (child.getContent().equals(content)) {
                return child;
            }
        }
        DecisionTree newChild = new DecisionTree(content);
        this.addChild(newChild);
        return newChild;
    }

    public DecisionTree hasMember(String objectId, String member) {
        String observation = objectId + ".hasMember(\"" + member + "\")";
        return findOrCreateChild(observation);
    }

    public DecisionTree getMember(String objectId, String member) {
        String observation = objectId + ".getMember(\"" + member + "\")";
        return findOrCreateChild(observation);
    }

    public DecisionTree putMember(String objectId, String member, String value) {
        String observation = objectId + ".putMember(\"" + member + "\", " + value + ")";
        return findOrCreateChild(observation);
    }

    public DecisionTree getMemberKeys(String objectId) {
        String observation = objectId + ".getMemberKeys()";
        return findOrCreateChild(observation);
    }

    public DecisionTree get(String objectId, long index) {
        String observation = objectId + ".get(" + index + ")";
        return findOrCreateChild(observation);
    }

    public DecisionTree set(String objectId, long index, String value) {
        String observation = objectId + ".set(" + index + ", " + value + ")";
        return findOrCreateChild(observation);
    }

    public DecisionTree getSize(String objectId) {
        String observation = objectId + ".getSize()";
        return findOrCreateChild(observation);
    }

    public DecisionTree execute(String objectId, int numArgs) {
        String observation = objectId + ".execute(" + numArgs + " args)";
        return findOrCreateChild(observation);
    }

    public DecisionTree crash(String message) {
        String crashMessage = "crash: " + message;
        return findOrCreateChild(crashMessage);
    }

    public DecisionTree answer(String answer) {
        return findOrCreateChild(answer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DecisionTree node = (DecisionTree) o;
        return Objects.equals(content, node.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toStringHelper(sb, 0);
        return sb.toString();
    }

    private void toStringHelper(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
        sb.append(content);
        sb.append("\n");
        for (DecisionTree child : children) {
            child.toStringHelper(sb, indent + 1);
        }
    }
}
