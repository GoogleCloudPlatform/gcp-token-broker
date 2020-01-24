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

package com.google.cloud.broker.apps.brokerserver.sessions;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.cloud.broker.database.models.Model;

public class SessionTest {

    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final String YARN = "yarn@FOO.BAR";
    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String MOCK_BUCKET = "gs://example";

    // TODO: testToMap

    @Test
    public void testFromMap() {
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "123456789");
        values.put("owner", ALICE);
        values.put("renewer", YARN);
        values.put("target", MOCK_BUCKET);
        values.put("scopes", GCS);
        values.put("password", "abcd");
        values.put("creationTime", 11111111111111L);
        values.put("expiresAt", 2222222222222L);

        Session session = (Session) Model.fromMap(Session.class, values);
        assertEquals("123456789", session.getId());
        assertEquals(ALICE, session.getOwner());
        assertEquals(YARN, session.getRenewer());
        assertEquals(MOCK_BUCKET, session.getTarget());
        assertEquals(GCS, session.getScopes());
        assertEquals("abcd", session.getPassword());
        assertEquals(11111111111111L, session.getCreationTime().longValue());
        assertEquals(2222222222222L, session.getExpiresAt().longValue());
    }

}
