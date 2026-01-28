package de.hpi.swa.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.coverage.Coverage;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class CoverageAdapter extends TypeAdapter<Coverage> {

    @Override
    public void write(JsonWriter out, Coverage coverage) throws IOException {
        if (coverage == null) {
            out.nullValue();
            return;
        }

        Map<Source, Set<Integer>> linesByFile = Coverage.linesByFile(coverage.getCovered());
        
        out.beginObject();
        out.name("totalSections").value(coverage.getCovered().size());
        out.name("files").beginArray();
        
        for (Map.Entry<Source, Set<Integer>> entry : linesByFile.entrySet()) {
            Source source = entry.getKey();
            Set<Integer> lines = entry.getValue();
            
            out.beginObject();
            out.name("name").value(source.getName());
            String path = source.getPath();
            if (path != null) {
                out.name("path").value(path);
            }
            out.name("lines").beginArray();
            for (Integer line : lines.stream().sorted().toList()) {
                out.value(line);
            }
            out.endArray();
            out.endObject();
        }
        
        out.endArray();
        out.endObject();
    }

    @Override
    public Coverage read(JsonReader in) throws IOException {
        throw new UnsupportedOperationException("Coverage deserialization not implemented");
    }
}
