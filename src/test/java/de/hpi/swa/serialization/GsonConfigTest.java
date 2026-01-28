package de.hpi.swa.serialization;

import com.google.gson.Gson;

import org.junit.Test;
import static org.junit.Assert.*;

public class GsonConfigTest {

    @Test
    public void testCreateGsonNotNull() {
        Gson gson = GsonConfig.createGson();
        assertNotNull(gson);
    }

    @Test
    public void testGsonConfigReturnsDifferentInstances() {
        Gson gson1 = GsonConfig.createGson();
        Gson gson2 = GsonConfig.createGson();
        
        assertNotNull(gson1);
        assertNotNull(gson2);
        assertNotSame(gson1, gson2);
    }

    @Test
    public void testDoubleNaNSerializesAsNull() {
        Gson gson = GsonConfig.createGson();
        
        Double nanValue = Double.NaN;
        String json = gson.toJson(nanValue);
        
        assertEquals("null", json);
    }

    @Test
    public void testDoublePrimitiveNaNSerializesAsNull() {
        Gson gson = GsonConfig.createGson();
        
        double nanValue = Double.NaN;
        String json = gson.toJson(nanValue);
        
        assertEquals("null", json);
    }

    @Test
    public void testNormalDoubleSerializesCorrectly() {
        Gson gson = GsonConfig.createGson();
        
        Double value = 3.14159;
        String json = gson.toJson(value);
        
        assertEquals("3.14159", json);
    }

    @Test
    public void testDoubleInfinitySerializesAsNull() {
        Gson gson = GsonConfig.createGson();
        
        Double posInf = Double.POSITIVE_INFINITY;
        Double negInf = Double.NEGATIVE_INFINITY;
        
        assertEquals("null", gson.toJson(posInf));
        assertEquals("null", gson.toJson(negInf));
    }
}
