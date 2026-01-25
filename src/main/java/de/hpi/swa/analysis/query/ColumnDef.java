package de.hpi.swa.analysis.query;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import de.hpi.swa.analysis.operations.Materializer;
import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner;
import de.hpi.swa.generator.Shape;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Runner.RunResult;

public sealed interface ColumnDef<T> {

    ColumnId<T> id();

    default String name() {
        return id().name();
    }

    public record ColumnId<T>(String name) {
        public static <T> ColumnId<T> of(String name) {
            return new ColumnId<>(name);
        }
    }

    public record RowCtx(Function<ColumnDef<?>, Object> materialize) {
        @SuppressWarnings("unchecked")
        public <T> T get(ColumnDef<T> column) {
            return (T) materialize.apply(column);
        }
    }

    interface Preparable {
        void prepare(List<RunResult> results, Pool pool, Materializer materializer);

        boolean isPrepared();
    }

    /**
     * Base column: direct extractor from the RunResult (e.g. input, output, trace)
     */
    public final record Base<T>(ColumnId<T> id, Function<RunResult, T> extractor) implements ColumnDef<T> {
        public Base {
            Objects.requireNonNull(id);
            Objects.requireNonNull(extractor);
        }
    }

    /**
     * Key-level computed column:
     * - depends on a single key source column (whose values come from a small
     * domain)
     * - result is cached per unique key value
     */
    public final record KeyComputed<K, T>(ColumnId<T> id, ColumnDef<K> keySource, Function<K, T> compute)
            implements ColumnDef<T> {
        public KeyComputed {
            Objects.requireNonNull(id);
            Objects.requireNonNull(keySource);
            Objects.requireNonNull(compute);
        }
    }

    /**
     * Row-level computed column:
     * - computed per row using RowCtx to access other columns
     * - result is cached per row during materialization
     */
    public final record RowComputed<T>(ColumnId<T> id, Function<RowCtx, T> compute)
            implements ColumnDef<T> {
        public RowComputed {
            Objects.requireNonNull(id);
            Objects.requireNonNull(compute);
        }
    }

    /**
     * Preparable Key-level column (stateful, needs preparation)
     * e.g. for heuristics:
     * - depends on a key source column
     * - result is cached per unique key value
     */
    public abstract non-sealed class PreparableKeyColumn<K, T> implements ColumnDef<T>, Preparable {
        private final ColumnId<T> id;
        private final ColumnDef<K> keySource;
        private boolean prepared = false;

        protected PreparableKeyColumn(ColumnId<T> id, ColumnDef<K> keySource) {
            this.id = Objects.requireNonNull(id);
            this.keySource = Objects.requireNonNull(keySource);
        }

        @Override
        public final ColumnId<T> id() {
            return id;
        }

        public final ColumnDef<K> keySource() {
            return keySource;
        }

        @Override
        public final boolean isPrepared() {
            return prepared;
        }

        @Override
        public final void prepare(List<RunResult> results, Pool pool, Materializer materializer) {
            doPrepare(results, pool, materializer);
            prepared = true;
        }

        protected abstract void doPrepare(List<RunResult> results, Pool pool, Materializer materializer);

        public abstract T compute(K keyValue);
    }

    /**
     * Preparable row-level column (stateful, needs preparation):
     * e.g. for heuristics:
     * - result is cached per row during materialization
     */
    public abstract non-sealed class PreparableRowColumn<T> implements ColumnDef<T>, Preparable {
        private final ColumnId<T> id;
        private boolean prepared = false;

        protected PreparableRowColumn(ColumnId<T> id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public final ColumnId<T> id() {
            return id;
        }

        @Override
        public final boolean isPrepared() {
            return prepared;
        }

        @Override
        public final void prepare(List<RunResult> results, Pool pool, Materializer materializer) {
            doPrepare(results, pool, materializer);
            prepared = true;
        }

        protected abstract void doPrepare(List<RunResult> results, Pool pool, Materializer materializer);

        public abstract T compute(RunResult row);
    }

    static ColumnDef<Shape> INPUT_SHAPE = new Base<>(ColumnId.of("InputShape"),
            rr -> Shape.fromValue(rr.input(), rr.universe()));

    static ColumnDef<String> INPUT_TYPE = new Base<>(ColumnId.of("InputType"),
            rr -> Shape.fromValue(rr.input(), rr.universe()).typeName());

    static ColumnDef<Trace> TRACE = new Base<>(ColumnId.of("Trace"), rr -> rr.trace());

    static ColumnDef<String> OUTPUT_TYPE = new Base<>(ColumnId.of("OutputType"), rr -> switch (rr.output()) {
        case Runner.FunctionResult.Normal(var typeName, var value) -> typeName;
        case Runner.FunctionResult.Crash(var msg, var stackTrace) -> "Crash";
    });

    static ColumnDef<String> OUTPUT_SHAPE = new Base<>(ColumnId.of("OutputShape"), rr -> switch (rr.output()) {
        case Runner.FunctionResult.Normal(var typeName, var value) -> typeName;
        case Runner.FunctionResult.Crash(var msg, var stackTrace) ->
            "Crash:" + (msg != null ? msg.split(":")[0] : "Unknown");
    });

    static ColumnDef<String> EXCEPTION_TYPE = new Base<>(ColumnId.of("ExceptionType"), rr -> switch (rr.output()) {
        case Runner.FunctionResult.Normal(var typeName, var value) -> null;
        case Runner.FunctionResult.Crash(var msg, var stackTrace) ->
            msg != null ? msg.split(":")[0] : "UnknownException";
    });

    static ColumnDef<Boolean> IS_CRASH = new KeyComputed<>(ColumnId.of("IsCrash"), OUTPUT_TYPE,
            outputType -> outputType.equals("Crash"));

    class CoveragePath extends PreparableKeyColumn<Trace, Coverage> {
        public static final ColumnId<Coverage> ID = ColumnId.of("CoveragePath");

        private Pool pool;

        public CoveragePath() {
            super(ID, TRACE);
        }

        @Override
        protected void doPrepare(List<RunResult> results, Pool pool, Materializer materializer) {
            this.pool = pool;
        }

        @Override
        public Coverage compute(Trace trace) {
            try {
                return pool.getCoverage(trace);
            } catch (IllegalArgumentException e) {
                return new Coverage();
            }
        }
    }

    static CoveragePath COVERAGE_PATH = new CoveragePath();
}
