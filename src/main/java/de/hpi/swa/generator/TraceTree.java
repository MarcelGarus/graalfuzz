package de.hpi.swa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TraceTree {

    public sealed interface Event {

        record Root() implements Event {
        }

        record Run(String arg) implements Event {

        }

        record DecideMemberExists(int objectId, String key, String value) implements Event {

        }

        record DecideMemberDoesNotExist(int objectId, String key) implements Event {

        }

        record Returns(String value) implements Event {

        }

        record Crash(String message) implements Event {

        }
    }

    private final Event event;
    private final boolean wasForced;
    public int numVisits;
    private final List<TraceTree> children;

    public TraceTree() {
        this.event = new Event.Root();
        this.wasForced = true;
        this.numVisits = 0;
        this.children = new ArrayList<>();
    }

    public TraceTree(Event event, boolean wasForced) {
        this.event = event;
        this.wasForced = wasForced;
        this.numVisits = 1;
        this.children = new ArrayList<>();
    }

    public void addChild(TraceTree child) {
        children.add(child);
    }

    public List<TraceTree> getChildren() {
        return children;
    }

    public Event getContent() {
        return event;
    }

    public TraceTree add(Event content, boolean wasForced) {
        for (TraceTree child : children) {
            if (child.getContent().equals(content)) {
                child.numVisits++;
                return child;
            }
        }
        TraceTree newChild = new TraceTree(content, wasForced);
        this.addChild(newChild);
        return newChild;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TraceTree node = (TraceTree) o;
        return Objects.equals(event, node.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toStringHelper(sb, 0);
        return sb.toString();
    }

    private void toStringHelper(StringBuilder sb, int indent) {
        sb.append("| ".repeat(indent));
        switch (event) {
            case Event.Root root ->
                sb.append("<root>");
            case Event.Run run ->
                sb.append("run(" + run.arg + ")");
            // case Event.AccessMember access ->
            //     sb.append(access.objectId + "." + access.key);
            case Event.DecideMemberExists member ->
                sb.append("object_" + member.objectId + "." + member.key + " = " + member.value);
            case Event.DecideMemberDoesNotExist member ->
                sb.append("object_" + member.objectId + "." + member.key + " does not exist");
            case Event.Returns returns ->
                sb.append("return " + returns.value
                );
            case Event.Crash crash ->
                sb.append("crash: " + crash.message);
        };
        if (wasForced) {
            sb.append(" (forced)");
        }
        sb.append(" [");
        sb.append(numVisits);
        sb.append(" runs]");
        sb.append("\n");
        for (TraceTree child : children) {
            child.toStringHelper(sb, indent + 1);
        }
    }
}
