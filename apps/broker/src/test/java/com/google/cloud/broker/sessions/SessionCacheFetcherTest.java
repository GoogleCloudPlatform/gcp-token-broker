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

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;

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
        AppSettings.setProperty(AppSettings.SESSION_LOCAL_CACHE_TIME, "1234");
        AppSettings.setProperty(AppSettings.SESSION_RENEW_PERIOD, SESSION_RENEW_PERIOD.toString());
        AppSettings.setProperty(AppSettings.SESSION_MAXIMUM_LIFETIME, SESSION_MAXIMUM_LIFETIME.toString());
        AppSettings.setProperty(AppSettings.DATABASE_BACKEND, "com.google.cloud.broker.database.backends.DummyDatabaseBackend");
        AppSettings.setProperty(AppSettings.ENCRYPTION_BACKEND, "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
    }

    private Session createSession() {
        // Create a session in the database
        Session session = new Session(null, ALICE, "yarn@FOO.BAR", MOCK_BUCKET, GCS, null, null, null);
        AbstractDatabaseBackend.getInstance().save(session);
        return session;
    }

    @Test
    public void testComputeResult() {
        Session session = createSession();
        String rawToken = SessionTokenUtils.marshallSessionToken(session);
        SessionCacheFetcher fetcher = new SessionCacheFetcher(rawToken);
        Session computed = (Session) fetcher.computeResult();
        assertEquals(session.getId(), computed.getId());
    }

    @Test
    public void testFromJSON() {
        SessionCacheFetcher fetcher = new SessionCacheFetcher("xxxx");
        String json = "{" +
            "\"id\": \"abcd\", " +
            "\"creationTime\": 1000000000000, " +
            "\"owner\": \"bob@EXAMPLE.COM\", " +
            "\"renewer\": \"yarn@BAZ.NET\", " +
            "\"scope\": \"" + BIGQUERY + "\", " +
            "\"target\": \"gs://blah\", " +
            "\"password\": \"secret!\", " +
            "\"expiresAt\": 2000000000000" +
        "}";
        Session session;
        try {
            session = (Session) fetcher.fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals("abcd", session.getId());
        assertEquals(1000000000000L, session.getCreationTime().longValue());
        assertEquals("bob@EXAMPLE.COM", session.getOwner());
        assertEquals("yarn@BAZ.NET", session.getRenewer());
        assertEquals(BIGQUERY, session.getScope());
        assertEquals("gs://blah", session.getTarget());
        assertEquals("secret!", session.getPassword());
        assertEquals(2000000000000L, session.getExpiresAt().longValue());
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

}
