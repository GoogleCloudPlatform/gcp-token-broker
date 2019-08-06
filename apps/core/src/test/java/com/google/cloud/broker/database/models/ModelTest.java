// Copyright 2019 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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