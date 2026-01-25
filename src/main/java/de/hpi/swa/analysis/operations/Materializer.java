package de.hpi.swa.analysis.operations;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.ColumnDef.Preparable;
import de.hpi.swa.generator.Pool;
import de.hpi.swa.generator.Runner.RunResult;

public final class Materializer {

    /*
     * Map<ColumnDef<T>, Map<K, T>> - persists for KeyComputed and
     * PreparedKeyHeuristic
     */
    private final Map<ColumnDef<?>, Map<?, ?>> keyCache = new ConcurrentHashMap<>();

    /*
     * Map<RunResult, Map<ColumnDef<T>, T>> - persists for RowComputed and
     * PreparedRowHeuristic
     */
    private final Map<RunResult, Map<ColumnDef<?>, Object>> rowCache = new IdentityHashMap<>();

    public void prepareAll(List<ColumnDef<?>> defs, List<RunResult> results, Pool pool) {
        for (ColumnDef<?> def : defs) {
            if (def instanceof Preparable preparable && !preparable.isPrepared()) {
                preparable.prepare(results, pool, this);
            }
        }
    }

    public <T> T materialize(RunResult row, ColumnDef<T> column) {
        Map<ColumnDef<?>, Object> perRowCache = rowCache.computeIfAbsent(row, r -> new IdentityHashMap<>());
        return materializeWithRowCache(row, column, perRowCache);
    }

    @SuppressWarnings("unchecked")
    private <T> T materializeWithRowCache(RunResult row, ColumnDef<T> column, Map<ColumnDef<?>, Object> perRowCache) {
        if (perRowCache.containsKey(column)) {
            return (T) perRowCache.get(column);
        }

        T result = switch (column) {
            case ColumnDef.Base<T> baseDef ->
                baseDef.extractor().apply(row);

            case ColumnDef.KeyComputed<?, T> keyComputedDef ->
                materializeKeyComputed(keyComputedDef, row, perRowCache);

            case ColumnDef.RowComputed<T> rowComputedDef -> {
                ColumnDef.RowCtx ctx = new ColumnDef.RowCtx(col -> materializeWithRowCache(row, col, perRowCache));
                yield rowComputedDef.compute().apply(ctx);
            }

            case ColumnDef.PreparableKeyColumn<?, T> preparedKeyDef ->
                materializePreparedKeyHeuristic(preparedKeyDef, row, perRowCache);

            case ColumnDef.PreparableRowColumn<T> preparedRowDef -> {
                if (!preparedRowDef.isPrepared()) {
                    throw new IllegalStateException("RowHeuristicColumn '" + column.name()
                            + "' must be prepared before use. Call Preparable.prepare() or Materializer.prepareAll() first.");
                }
                yield preparedRowDef.compute(row);
            }
        };

        perRowCache.put(column, result);
        return result;
    }

    private <K, T> T materializeKeyComputed(ColumnDef.KeyComputed<K, T> keyComputedDef, RunResult row,
            Map<ColumnDef<?>, Object> perRowCache) {
        K keyVal = materializeWithRowCache(row, keyComputedDef.keySource(), perRowCache);
        return retrieveFromKeyCache(keyComputedDef, keyVal, keyComputedDef.compute());
    }

    private <K, T> T materializePreparedKeyHeuristic(ColumnDef.PreparableKeyColumn<K, T> preparedKeyDef, RunResult row,
            Map<ColumnDef<?>, Object> perRowCache) {
        if (!preparedKeyDef.isPrepared()) {
            throw new IllegalStateException("KeyHeuristicColumn '" + preparedKeyDef.name()
                    + "' must be prepared before use. Call Preparable.prepare() or Materializer.prepareAll() first.");
        }
        K keyVal = materializeWithRowCache(row, preparedKeyDef.keySource(), perRowCache);
        return retrieveFromKeyCache(preparedKeyDef, keyVal, k -> preparedKeyDef.compute(k));
    }

    @SuppressWarnings("unchecked")
    private <K, T> T retrieveFromKeyCache(ColumnDef<T> column, K keyVal, Function<K, T> compute) {
        Map<K, T> perKeyMap = (Map<K, T>) keyCache.computeIfAbsent(column, k -> new ConcurrentHashMap<>());
        return perKeyMap.computeIfAbsent(keyVal, compute);
    }

    public void clearCaches() {
        keyCache.clear();
        rowCache.clear();
    }

    public void clearRowCache() {
        rowCache.clear();
    }
}