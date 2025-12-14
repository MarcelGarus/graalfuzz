package de.hpi.swa.analysis.grouping;

import java.util.List;
import java.util.stream.Collectors;
import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.generator.Value;
import de.hpi.swa.generator.Universe;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Trace.Return;
import de.hpi.swa.generator.Trace.Crash;
import de.hpi.swa.generator.Pool;

public sealed interface GroupKey {

    record InputShape(String value) implements GroupKey {
        public static InputShape from(Value value, Universe universe) {
            if (value instanceof Value.Null)
                return new InputShape("null");
            if (value instanceof Value.Boolean)
                return new InputShape("boolean");
            if (value instanceof Value.Int)
                return new InputShape("int");
            if (value instanceof Value.Double)
                return new InputShape("double");
            if (value instanceof Value.StringValue)
                return new InputShape("string");
            if (value instanceof Value.ObjectValue objVal) {
                var obj = universe.get(objVal.id());
                if (obj == null)
                    return new InputShape("object{}");
                String keys = obj.members.keySet().stream()
                        .sorted()
                        .collect(Collectors.joining(","));
                return new InputShape("object{" + keys + "}");
            }
            return new InputShape("unknown");
        }

        @Override
        public String toString() {
            return "InputShape:" + value;
        }
    }

    record PathHash(int hash, int length) implements GroupKey {
        public static PathHash from(Trace trace, Pool pool) {
            Coverage coverage = pool.getCoverage(trace);
            if (coverage == null) {
                return new PathHash(0, 0);
            }
            return new PathHash(coverage.hashCode(), coverage.getCovered().size());
        }

        @Override
        public String toString() {
            return "PathHash:" + hash;
        }
    }

    record OutputShape(String value) implements GroupKey {
        public static OutputShape from(Trace trace) {
            if (trace.entries.isEmpty())
                return new OutputShape("void");
            var last = trace.entries.get(trace.entries.size() - 1);
            if (last instanceof Return ret) {
                return new OutputShape(ret.typeName());
            }
            if (last instanceof Crash) {
                return new OutputShape("crash");
            }
            return new OutputShape("void");
        }

        @Override
        public String toString() {
            return "OutputShape:" + value;
        }
    }

    record ExceptionType(String value) implements GroupKey {
        public static ExceptionType from(Trace trace) {
            if (trace.entries.isEmpty())
                return new ExceptionType("None");
            var last = trace.entries.get(trace.entries.size() - 1);
            if (last instanceof Crash crash) {
                String msg = crash.message();
                int colonIndex = msg.indexOf(':');
                if (colonIndex > 0) {
                    return new ExceptionType(msg.substring(0, colonIndex).trim());
                }
                return new ExceptionType(msg);
            }
            return new ExceptionType("None");
        }

        @Override
        public String toString() {
            return "Exception:" + value;
        }
    }

    record Generic(String value) implements GroupKey {
        @Override
        public String toString() {
            return value;
        }
    }

    record Composite(List<GroupKey> parts) implements GroupKey {
        @Override
        public String toString() {
            return parts.stream().map(Object::toString).collect(Collectors.joining(" | "));
        }
    }
}
