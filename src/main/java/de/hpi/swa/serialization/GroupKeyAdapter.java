package de.hpi.swa.serialization;

import com.google.gson.*;

import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.Shape;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GroupKeyAdapter implements JsonSerializer<GroupKey> {

    @Override
    public JsonElement serialize(GroupKey key, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        switch (key) {
            case GroupKey.Root() -> {
                result.addProperty("type", "Root");
            }
            case GroupKey.Single<?> single -> {
                result.addProperty("type", "Single");
                result.addProperty("column", single.column().name());
                // Serialize the value with explicit type for proper adapter delegation
                result.add("value", serializeValue(single.value(), context));
            }
            case GroupKey.Composite composite -> {
                result.addProperty("type", "Composite");
                JsonArray partsArray = new JsonArray();
                for (GroupKey.Composite.Part<?> part : composite.parts()) {
                    JsonObject partObj = new JsonObject();
                    partObj.addProperty("column", part.column().name());
                    partObj.add("value", serializeValue(part.value(), context));
                    partsArray.add(partObj);
                }
                result.add("parts", partsArray);
            }
        }
        
        return result;
    }
    
    private JsonElement serializeValue(Object value, JsonSerializationContext context) {
        // Handle known types that require explicit type hints for proper serialization
        if (value instanceof Shape shape) {
            return context.serialize(shape, Shape.class);
        }
        // Default serialization for other types (String, Integer, etc.)
        return context.serialize(value);
    }
}
