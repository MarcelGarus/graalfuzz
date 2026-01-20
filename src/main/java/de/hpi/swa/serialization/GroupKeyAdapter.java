package de.hpi.swa.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.hpi.swa.analysis.grouping.GroupKey;
import java.lang.reflect.Type;

public class GroupKeyAdapter implements JsonSerializer<GroupKey> {
    @Override
    public JsonElement serialize(GroupKey src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        if (src instanceof GroupKey.InputShape inputShape) {
            jsonObject.addProperty("type", "InputShape");
            jsonObject.add("shape", context.serialize(inputShape.shape()));
        } else if (src instanceof GroupKey.PathHash pathHash) {
            jsonObject.addProperty("type", "PathHash");
            jsonObject.addProperty("hash", pathHash.hash());
            jsonObject.addProperty("length", pathHash.length());
        } else if (src instanceof GroupKey.OutputShape outputShape) {
            jsonObject.addProperty("type", "OutputShape");
            jsonObject.addProperty("value", outputShape.value());
        } else if (src instanceof GroupKey.ExceptionType exceptionType) {
            jsonObject.addProperty("type", "ExceptionType");
            jsonObject.addProperty("value", exceptionType.value());
        } else if (src instanceof GroupKey.Generic generic) {
            jsonObject.addProperty("type", "Generic");
            jsonObject.addProperty("value", generic.value());
        } else if (src instanceof GroupKey.Composite composite) {
            jsonObject.addProperty("type", "Composite");
            JsonArray partsArray = new JsonArray();
            for (GroupKey part : composite.parts()) {
                partsArray.add(serialize(part, part.getClass(), context));
            }
            jsonObject.add("parts", partsArray);
        } else {
            jsonObject.addProperty("type", "Unknown");
        }
        
        return jsonObject;
    }
}
