package de.hpi.swa.serialization;

import com.google.gson.*;

import de.hpi.swa.generator.Value;

import java.lang.reflect.Type;

public class ValueAdapter implements JsonSerializer<Value>, JsonDeserializer<Value> {

    @Override
    public JsonElement serialize(Value value, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        switch (value) {
            case Value.Null() -> {
                result.addProperty("type", "Null");
            }
            case Value.Boolean bool -> {
                result.addProperty("type", "Boolean");
                result.addProperty("value", bool.value());
            }
            case Value.Int intVal -> {
                result.addProperty("type", "Int");
                result.addProperty("value", intVal.value());
            }
            case Value.Double dbl -> {
                result.addProperty("type", "Double");
                result.addProperty("value", dbl.value());
            }
            case Value.StringValue str -> {
                result.addProperty("type", "String");
                result.addProperty("value", str.value());
            }
            case Value.ObjectValue obj -> {
                result.addProperty("type", "Object");
                result.add("id", context.serialize(obj.id()));
            }
        }
        
        return result;
    }

    @Override
    public Value deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String type = obj.get("type").getAsString();
        
        return switch (type) {
            case "Null" -> new Value.Null();
            case "Boolean" -> new Value.Boolean(obj.get("value").getAsBoolean());
            case "Int" -> new Value.Int(obj.get("value").getAsInt());
            case "Double" -> new Value.Double(obj.get("value").getAsDouble());
            case "String" -> new Value.StringValue(obj.get("value").getAsString());
            case "Object" -> new Value.ObjectValue(context.deserialize(obj.get("id"), Value.ObjectId.class));
            default -> throw new JsonParseException("Unknown Value type: " + type);
        };
    }
}
