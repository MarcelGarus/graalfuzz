package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionTree {

    private final Node root;

    public DecisionTree() {
        this.root = new Node("root");
    }

    public Node getRoot() {
        return root;
    }

    @Override
    public String toString() {
        return root.toString();
    }

    public static class Node {

        private final String content;
        private final List<Node> children;

        public Node(String content) {
            this.content = content;
            this.children = new ArrayList<>();
        }

        public void addChild(Node child) {
            children.add(child);
        }

        public List<Node> getChildren() {
            return children;
        }

        public String getContent() {
            return content;
        }

        private Node findOrCreateChild(String content) {
            for (Node child : children) {
                if (child.getContent().equals(content)) {
                    return child;
                }
            }
            Node newChild = new Node(content);
            this.addChild(newChild);
            return newChild;
        }

        public Node hasMember(String objectId, String member) {
            String observation = objectId + ".hasMember(\"" + member + "\")";
            return findOrCreateChild(observation);
        }

        public Node getMember(String objectId, String member) {
            String observation = objectId + ".getMember(\"" + member + "\")";
            return findOrCreateChild(observation);
        }

        public Node putMember(String objectId, String member, String value) {
            String observation = objectId + ".putMember(\"" + member + "\", " + value + ")";
            return findOrCreateChild(observation);
        }

        public Node getMemberKeys(String objectId) {
            String observation = objectId + ".getMemberKeys()";
            return findOrCreateChild(observation);
        }

        public Node get(String objectId, long index) {
            String observation = objectId + ".get(" + index + ")";
            return findOrCreateChild(observation);
        }

        public Node set(String objectId, long index, String value) {
            String observation = objectId + ".set(" + index + ", " + value + ")";
            return findOrCreateChild(observation);
        }

        public Node getSize(String objectId) {
            String observation = objectId + ".getSize()";
            return findOrCreateChild(observation);
        }

        public Node execute(String objectId, int numArgs) {
            String observation = objectId + ".execute(" + numArgs + " args)";
            return findOrCreateChild(observation);
        }

        public Node crash(String message) {
            String crashMessage = "crash: " + message;
            return findOrCreateChild(crashMessage);
        }

        public Node answer(String answer) {
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
            Node node = (Node) o;
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
            for (Node child : children) {
                child.toStringHelper(sb, indent + 1);
            }
        }
    }
}
