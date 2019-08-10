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

package com.google.cloud.broker.sessions;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;
import com.google.cloud.broker.database.models.Model;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.settings.AppSettings;

public class SessionCacheFetcherTest {

    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String BIGQUERY = "https://www.googleapis.com/auth/bigquery";
    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final String MOCK_BUCKET = "gs://example";
    private static final Long SESSION_RENEW_PERIOD = 80000000L;
    private static final Long SESSION_MAXIMUM_LIFETIME = 80000000L;


    @BeforeClass
    public static void setupClass() {
        AppSettings.reset();
        AppSettings.setProperty("SESSION_LOCAL_CACHE_TIME", "1234");
        AppSettings.setProperty("SESSION_RENEW_PERIOD", SESSION_RENEW_PERIOD.toString());
        AppSettings.setProperty("SESSION_MAXIMUM_LIFETIME", SESSION_MAXIMUM_LIFETIME.toString());
        AppSettings.setProperty("DATABASE_BACKEND", "com.google.cloud.broker.database.backends.DummyDatabaseBackend");
        AppSettings.setProperty("ENCRYPTION_BACKEND", "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
        AppSettings.setProperty("ENCRYPTION_DELEGATION_TOKEN_CRYPTO_KEY", "blah");
    }

    private Session createSession() {
        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", ALICE);
        values.put("renewer", "yarn@FOO.BAR");
        values.put("scope", GCS);
        values.put("target", MOCK_BUCKET);
        Session session = new Session(values);
        Model.save(session);
        return session;
    }

    @Test
    public void testComputeResult() {
        Session session = createSession();
        String rawToken = SessionTokenUtils.marshallSessionToken(session);
        SessionCacheFetcher fetcher = new SessionCacheFetcher(rawToken);
        Session computed = (Session) fetcher.computeResult();
        assertEquals(session.getValue("id"), computed.getValue("id"));
    }

    @Test
    public void testFromJSON() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        String json = "{" +
            "\"values\": {" +
                "\"id\": \"abcd\", " +
                "\"creation_time\": 1000000000000, " +
                "\"owner\": \"bob@EXAMPLE.COM\", " +
                "\"renewer\": \"yarn@BAZ.NET\", " +
                "\"scope\": \"" + BIGQUERY + "\", " +
                "\"target\": \"gs://blah\", " +
                "\"password\": \"secret!\", " +
                "\"expires_at\": 2000000000000" +
            "}" +
        "}";
        Session session;
        try {
            session = (Session) fetcher.fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals("abcd", session.getValue("id"));
        assertEquals(1000000000000L, session.getValue("creation_time"));
        assertEquals("bob@EXAMPLE.COM", session.getValue("owner"));
        assertEquals("yarn@BAZ.NET", session.getValue("renewer"));
        assertEquals(BIGQUERY, session.getValue("scope"));
        assertEquals("gs://blah", session.getValue("target"));
        assertEquals("secret!", session.getValue("password"));
        assertEquals(2000000000000L, session.getValue("expires_at"));
    }

    @Test
    public void testGetCacheKey() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        assertEquals(String.format("session-xxxx"), fetcher.getCacheKey());
    }

    @Test
    public void testGetLocalCacheTime() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        assertEquals(1234, fetcher.getLocalCacheTime());
    }

    @Test
    public void testGetRemoteCacheTime() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        try {
            fetcher.getRemoteCacheTime();
            fail();
        } catch(UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testGetRemoteCacheCryptoKey() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        try {
            fetcher.getRemoteCacheCryptoKey();
            fail();
        } catch(UnsupportedOperationException e) {
            // Expected
        }
    }

}
