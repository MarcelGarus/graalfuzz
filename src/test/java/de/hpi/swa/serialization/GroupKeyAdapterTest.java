package de.hpi.swa.serialization;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.hpi.swa.analysis.operations.Grouping.GroupKey;
import de.hpi.swa.analysis.query.ColumnDef;
import de.hpi.swa.analysis.query.Shape;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class GroupKeyAdapterTest {

    private final Gson gson = GsonConfig.createGson();

    @Test
    public void testSerializeRootKey() {
        GroupKey key = new GroupKey.Root();
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Root", obj.get("type").getAsString());
    }

    @Test
    public void testSerializeSingleKeyWithString() {
        ColumnDef<String> column = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputType"), r -> "int");
        GroupKey key = new GroupKey.Single<>(column, "int");
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Single", obj.get("type").getAsString());
        assertEquals("OutputType", obj.get("column").getAsString());
        assertEquals("int", obj.get("value").getAsString());
    }

    @Test
    public void testSerializeSingleKeyWithShape() {
        ColumnDef<Shape> column = new ColumnDef.Base<>(ColumnDef.ColumnId.of("InputShape"), r -> new Shape.Int());
        GroupKey key = new GroupKey.Single<>(column, new Shape.Int());
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Single", obj.get("type").getAsString());
        assertEquals("InputShape", obj.get("column").getAsString());
        assertTrue(obj.has("value"));
        
        JsonObject value = obj.getAsJsonObject("value");
        assertEquals("Int", value.get("type").getAsString());
    }

    @Test
    public void testSerializeSingleKeyWithInteger() {
        ColumnDef<Integer> column = new ColumnDef.Base<>(ColumnDef.ColumnId.of("Count"), r -> 42);
        GroupKey key = new GroupKey.Single<>(column, 42);
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Single", obj.get("type").getAsString());
        assertEquals("Count", obj.get("column").getAsString());
        assertEquals(42, obj.get("value").getAsInt());
    }

    @Test
    public void testSerializeCompositeKeyWithTwoParts() {
        ColumnDef<String> col1 = new ColumnDef.Base<>(ColumnDef.ColumnId.of("InputType"), r -> "int");
        ColumnDef<String> col2 = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputType"), r -> "string");
        
        GroupKey.Composite.Part<String> part1 = new GroupKey.Composite.Part<>(col1, "int");
        GroupKey.Composite.Part<String> part2 = new GroupKey.Composite.Part<>(col2, "string");
        
        GroupKey key = new GroupKey.Composite(List.of(part1, part2));
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Composite", obj.get("type").getAsString());
        assertTrue(obj.has("parts"));
        
        JsonArray parts = obj.getAsJsonArray("parts");
        assertEquals(2, parts.size());
        
        JsonObject firstPart = parts.get(0).getAsJsonObject();
        assertEquals("InputType", firstPart.get("column").getAsString());
        assertEquals("int", firstPart.get("value").getAsString());
        
        JsonObject secondPart = parts.get(1).getAsJsonObject();
        assertEquals("OutputType", secondPart.get("column").getAsString());
        assertEquals("string", secondPart.get("value").getAsString());
    }

    @Test
    public void testSerializeCompositeKeyWithShapes() {
        ColumnDef<Shape> col1 = new ColumnDef.Base<>(ColumnDef.ColumnId.of("InputShape"), r -> new Shape.Int());
        ColumnDef<Shape> col2 = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputShape"), r -> new Shape.StringShape());
        
        GroupKey.Composite.Part<Shape> part1 = new GroupKey.Composite.Part<>(col1, new Shape.Int());
        GroupKey.Composite.Part<Shape> part2 = new GroupKey.Composite.Part<>(col2, new Shape.StringShape());
        
        GroupKey key = new GroupKey.Composite(List.of(part1, part2));
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Composite", obj.get("type").getAsString());
        
        JsonArray parts = obj.getAsJsonArray("parts");
        assertEquals(2, parts.size());
        
        JsonObject firstPart = parts.get(0).getAsJsonObject();
        assertEquals("InputShape", firstPart.get("column").getAsString());
        JsonObject firstValue = firstPart.getAsJsonObject("value");
        assertEquals("Int", firstValue.get("type").getAsString());
        
        JsonObject secondPart = parts.get(1).getAsJsonObject();
        assertEquals("OutputShape", secondPart.get("column").getAsString());
        JsonObject secondValue = secondPart.getAsJsonObject("value");
        assertEquals("String", secondValue.get("type").getAsString());
    }

    @Test
    public void testSerializeCompositeKeyEmpty() {
        GroupKey key = new GroupKey.Composite(List.of());
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Composite", obj.get("type").getAsString());
        assertTrue(obj.has("parts"));
        
        JsonArray parts = obj.getAsJsonArray("parts");
        assertEquals(0, parts.size());
    }

    @Test
    public void testSerializeCompositeKeyWithMixedTypes() {
        ColumnDef<Shape> shapeCol = new ColumnDef.Base<>(ColumnDef.ColumnId.of("InputShape"), r -> new Shape.Boolean());
        ColumnDef<String> stringCol = new ColumnDef.Base<>(ColumnDef.ColumnId.of("OutputType"), r -> "int");
        ColumnDef<Integer> intCol = new ColumnDef.Base<>(ColumnDef.ColumnId.of("Count"), r -> 10);
        
        GroupKey.Composite.Part<Shape> part1 = new GroupKey.Composite.Part<>(shapeCol, new Shape.Boolean());
        GroupKey.Composite.Part<String> part2 = new GroupKey.Composite.Part<>(stringCol, "int");
        GroupKey.Composite.Part<Integer> part3 = new GroupKey.Composite.Part<>(intCol, 10);
        
        GroupKey key = new GroupKey.Composite(List.of(part1, part2, part3));
        
        String json = gson.toJson(key, GroupKey.class);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        
        assertEquals("Composite", obj.get("type").getAsString());
        
        JsonArray parts = obj.getAsJsonArray("parts");
        assertEquals(3, parts.size());
        
        JsonObject firstPart = parts.get(0).getAsJsonObject();
        assertEquals("InputShape", firstPart.get("column").getAsString());
        assertEquals("Boolean", firstPart.getAsJsonObject("value").get("type").getAsString());
        
        JsonObject secondPart = parts.get(1).getAsJsonObject();
        assertEquals("OutputType", secondPart.get("column").getAsString());
        assertEquals("int", secondPart.get("value").getAsString());
        
        JsonObject thirdPart = parts.get(2).getAsJsonObject();
        assertEquals("Count", thirdPart.get("column").getAsString());
        assertEquals(10, thirdPart.get("value").getAsInt());
    }
}
