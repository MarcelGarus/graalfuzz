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

        record Return(String value) implements Event {

        }

        record Crash(String message) implements Event {

        }
    }

    private final Event event;
    private int numVisits;
    private final List<TraceTree> children;

    public TraceTree() {
        this.event = new Event.Root();
        this.numVisits = 0;
        this.children = new ArrayList<>();
    }

    public TraceTree(Event event, boolean wasForced) {
        this.event = event;
        this.numVisits = 1;
        this.children = new ArrayList<>();
    }

    public void visit() {
        numVisits++;
    }

    public TraceTree add(Event event) {
        for (TraceTree child : children) {
            if (child.event.equals(event)) {
                child.visit();
                return child;
            }
        }
        TraceTree newChild = new TraceTree(event, false);
        children.add(newChild);
        return newChild;
    }

    // public boolean isWorthExploring() {
    //     if (children.size() == 1) {
    //         var childEvent = children.get(0).event;
    //         if (childEvent instanceof Event.Return || childEvent instanceof Event.Crash) {
    //             return false;
    //         }
    //     }
    //     return true;
    // }
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

    private int qualityScore() {
        return (anySuccessfulRun() ? 1000 : 0) + 10 * depth() + numVisits;
    }

    private int depth() {
        return 1 + children.stream().mapToInt((child) -> child.depth()).max().orElse(0);
    }

    private boolean anySuccessfulRun() {
        if (event instanceof Event.Return) {
            return true;
        }
        return children.stream().anyMatch((child) -> child.anySuccessfulRun());
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREY = "\u001B[90m";
    public static final int MAX_CHILDREN = 7;

    private void toStringHelper(StringBuilder sb, int indent) {
        sb.append(ANSI_GREY).append("| ".repeat(indent)).append(ANSI_RESET);
        switch (event) {
            case Event.Root root ->
                sb.append("<root>");
            case Event.Run run ->
                sb.append("run(").append(ANSI_BLUE).append(run.arg).append(ANSI_RESET).append(")");
            case Event.DecideMemberExists member -> {
                sb.append(ANSI_BLUE).append("object_").append(member.objectId).append(ANSI_RESET)
                        .append(".").append(member.key).append(" = ")
                        .append(ANSI_BLUE).append(member.value).append(ANSI_RESET);
            }
            case Event.DecideMemberDoesNotExist member ->
                sb.append(ANSI_BLUE).append("object_").append(member.objectId).append(ANSI_RESET)
                        .append(".").append(member.key)
                        .append(" does not exist");
            case Event.Return returns -> {
                sb.append(ANSI_GREEN).append("return ")
                        .append(ANSI_BLUE).append(returns.value).append(ANSI_RESET);
            }
            case Event.Crash crash -> {
                sb.append(ANSI_RED).append("crash: ").append(crash.message).append(ANSI_RESET);
            }
        }
        sb.append(ANSI_GREY).append(" [").append(numVisits).append(" runs]").append(ANSI_RESET).append("\n");
        children.sort((a, b) -> b.qualityScore() - a.qualityScore());
        if (children.size() <= MAX_CHILDREN) {
            for (TraceTree child : children) {
                child.toStringHelper(sb, indent + 1);
            }
        } else {
            for (TraceTree child : children.subList(0, MAX_CHILDREN)) {
                child.toStringHelper(sb, indent + 1);
            }
            sb.append(ANSI_GREY).append("| ".repeat(indent + 1)).append(ANSI_RESET);
            var numVisitsRest = children.subList(MAX_CHILDREN, children.size()).stream().mapToInt((child) -> child.numVisits).sum();
            sb.append("... ").append(ANSI_GREY).append("[").append(numVisitsRest).append(" runs]").append(ANSI_RESET).append("\n");
        }
    }
}
