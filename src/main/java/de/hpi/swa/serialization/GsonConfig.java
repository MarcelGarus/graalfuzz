package de.hpi.swa.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.hpi.swa.generator.Trace;
import de.hpi.swa.generator.Value;

public class GsonConfig {
    
    public static Gson createGson() {
        return configure(new GsonBuilder()).create();
    }

    public static GsonBuilder configure(GsonBuilder builder) {
        return builder
            .registerTypeAdapter(Trace.TraceEntry.class, new TraceEntryAdapter())
            .registerTypeAdapter(Value.class, new ValueAdapter());
    }
}
