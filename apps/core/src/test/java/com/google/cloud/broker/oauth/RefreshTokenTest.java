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

package com.google.cloud.broker.oauth;

import java.util.HashMap;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.cloud.broker.database.models.Model;

public class RefreshTokenTest {

    // TODO: testToMap

    @Test
    public void testFromMap() {
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creationTime", 2222222222222L);
        values.put("value", "xyz".getBytes());

        RefreshToken token = (RefreshToken) Model.fromMap(RefreshToken.class, values);
        assertEquals("alice@example.com", token.getId());
        assertEquals(2222222222222L, token.getCreationTime().longValue());
        assertArrayEquals("xyz".getBytes(), token.getValue());
    }

}