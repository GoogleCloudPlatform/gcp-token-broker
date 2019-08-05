package com.google.cloud.broker.database.models;

import java.util.HashMap;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.cloud.broker.oauth.RefreshToken;

public class ModelTest {

    @Test
    public void testNewModelInstance() {
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creation_time", 2222222222222L);
        values.put("value", "xyz".getBytes());
        Model model = Model.newModelInstance(RefreshToken.class, values);

        assertTrue(model instanceof RefreshToken);
        assertEquals("alice@example.com", model.getValue("id"));
        assertEquals(2222222222222L, model.getValue("creation_time"));
        assertArrayEquals("xyz".getBytes(), (byte[]) model.getValue("value"));
    }

}