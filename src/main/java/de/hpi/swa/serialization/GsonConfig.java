package de.hpi.swa.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.query.Shape;
import de.hpi.swa.coverage.Coverage;
import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Value;
import de.hpi.swa.generator.Runner;

public class GsonConfig {

    public static Gson createGson() {
        return configure(new GsonBuilder()).create();
    }

    public static GsonBuilder configure(GsonBuilder builder) {
        return builder
                .registerTypeAdapter(Double.class, new NaNAsNullAdapter())
                .registerTypeAdapter(double.class, new NaNAsNullAdapter())
                .registerTypeAdapter(Runner.RunResult.class, new RunResultAdapter())
                .registerTypeAdapter(Trace.TraceEntry.class, new TraceEntryAdapter())
                .registerTypeAdapter(Value.class, new ValueAdapter())
                .registerTypeAdapter(Shape.class, new ShapeAdapter())
                .registerTypeAdapter(GroupKey.class, new GroupKeyAdapter())
                .registerTypeAdapter(Coverage.class, new CoverageAdapter());
    }
}
