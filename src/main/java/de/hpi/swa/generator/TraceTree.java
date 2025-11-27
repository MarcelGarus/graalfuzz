package de.hpi.swa.generator;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.Objects;

// import de.hpi.swa.coverage.Coverage;

public class TraceTree {

    // public sealed interface Event {
    //     record Root() implements Event {
    //     }
    //     record Run(String arg) implements Event {
    //     }
    //     record DecideMemberExists(int objectId, String key, String value) implements Event {
    //     }
    //     record DecideMemberDoesNotExist(int objectId, String key) implements Event {
    //     }
    //     record Return(String value) implements Event {
    //     }
    //     record Crash(String message) implements Event {
    //         public Crash(String message) {
    //         }
    //     }
    // }
    // private final Event event;
    // private int numVisits;
    // private final List<TraceTree> children;
    // public TraceTree() {
    //     this.event = new Event.Root();
    //     this.numVisits = 0;
    //     this.children = new ArrayList<>();
    // }
    // public TraceTree(Event event, boolean wasForced) {
    //     this.event = event;
    //     this.numVisits = 1;
    //     this.children = new ArrayList<>();
    // }
    // public void visit() {
    //     numVisits++;
    // }
    // public TraceTree add(Event event) {
    //     for (TraceTree child : children) {
    //         if (child.event.equals(event)) {
    //             child.visit();
    //             return child;
    //         }
    //     }
    //     TraceTree newChild = new TraceTree(event, false);
    //     children.add(newChild);
    //     return newChild;
    // }
    // // public boolean isWorthExploring() {
    // //     if (children.size() == 1) {
    // //         var childEvent = children.get(0).event;
    // //         if (childEvent instanceof Event.Return || childEvent instanceof Event.Crash) {
    // //             return false;
    // //         }
    // //     }
    // //     return true;
    // // }
    // @Override
    // public boolean equals(Object o) {
    //     if (this == o) {
    //         return true;
    //     }
    //     if (o == null || getClass() != o.getClass()) {
    //         return false;
    //     }
    //     TraceTree node = (TraceTree) o;
    //     return Objects.equals(event, node.event);
    // }
    // @Override
    // public int hashCode() {
    //     return Objects.hash(event);
    // }
    // @Override
    // public String toString() {
    //     StringBuilder sb = new StringBuilder();
    //     var allCoverages = collectAllCoverages();
    //     var unionedCoverage = Coverage.union(allCoverages.toArray(new Coverage[0]));
    //     toStringHelper(sb, 0, unionedCoverage);
    //     return sb.toString();
    // }
    // private List<Coverage> collectAllCoverages() {
    //     List<Coverage> coverages = new ArrayList<>();
    //     collectAllCoveragesHelper(coverages);
    //     return coverages;
    // }
    // private void collectAllCoveragesHelper(List<Coverage> coverages) {
    //     switch (event) {
    //         case Event.Return returns ->
    //             coverages.add(returns.coverage);
    //         case Event.Crash crash ->
    //             coverages.add(crash.coverage);
    //         default -> {
    //             // For other events, collect from children
    //         }
    //     }
    //     for (TraceTree child : children) {
    //         child.collectAllCoveragesHelper(coverages);
    //     }
    // }
    // private int qualityScore() {
    //     return (anySuccessfulRun() ? 1000 : 0) + 10 * depth() + numVisits;
    // }
    // private int depth() {
    //     return 1 + children.stream().mapToInt((child) -> child.depth()).max().orElse(0);
    // }
    // private boolean anySuccessfulRun() {
    //     if (event instanceof Event.Return) {
    //         return true;
    //     }
    //     return children.stream().anyMatch((child) -> child.anySuccessfulRun());
    // }
    // public static final String ANSI_RESET = "\u001B[0m";
    // public static final String ANSI_RED = "\u001B[31m";
    // public static final String ANSI_GREEN = "\u001B[32m";
    // public static final String ANSI_BLUE = "\u001B[34m";
    // public static final String ANSI_GREY = "\u001B[90m";
    // public static final int MAX_CHILDREN = 7;
    // private void toStringHelper(StringBuilder sb, int indent, Coverage unionedCoverage) {
    //     sb.append(ANSI_GREY).append("| ".repeat(indent)).append(ANSI_RESET);
    //     switch (event) {
    //         case Event.Root root ->
    //             sb.append("<root>");
    //         case Event.Run run ->
    //             sb.append("run(").append(ANSI_BLUE).append(run.arg).append(ANSI_RESET).append(")");
    //         case Event.DecideMemberExists member -> {
    //             sb.append(ANSI_BLUE).append("object_").append(member.objectId).append(ANSI_RESET)
    //                     .append(".").append(member.key).append(" = ")
    //                     .append(ANSI_BLUE).append(member.value).append(ANSI_RESET);
    //         }
    //         case Event.DecideMemberDoesNotExist member ->
    //             sb.append(ANSI_BLUE).append("object_").append(member.objectId).append(ANSI_RESET)
    //                     .append(".").append(member.key)
    //                     .append(" does not exist");
    //         case Event.Return returns -> {
    //             sb.append(ANSI_GREEN).append("return ")
    //                     .append(ANSI_BLUE).append(returns.value).append(ANSI_RESET).append(" ").append(returns.coverage.toString(unionedCoverage));
    //         }
    //         case Event.Crash crash -> {
    //             sb.append(ANSI_RED).append("crash: ").append(crash.message).append(ANSI_RESET).append(" ").append(crash.coverage.toString(unionedCoverage));
    //         }
    //     }
    //     sb.append(ANSI_GREY).append(" [").append(numVisits).append(" runs]").append(ANSI_RESET).append("\n");
    //     children.sort((a, b) -> b.qualityScore() - a.qualityScore());
    //     if (children.size() <= MAX_CHILDREN) {
    //         for (TraceTree child : children) {
    //             child.toStringHelper(sb, indent + 1, unionedCoverage);
    //         }
    //     } else {
    //         for (TraceTree child : children.subList(0, MAX_CHILDREN)) {
    //             child.toStringHelper(sb, indent + 1, unionedCoverage);
    //         }
    //         sb.append(ANSI_GREY).append("| ".repeat(indent + 1)).append(ANSI_RESET);
    //         var numVisitsRest = children.subList(MAX_CHILDREN, children.size()).stream().mapToInt((child) -> child.numVisits).sum();
    //         sb.append("... ").append(ANSI_GREY).append("[").append(numVisitsRest).append(" runs]").append(ANSI_RESET).append("\n");
    //     }
    // }
}
