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

package com.google.cloud.broker.apps.brokerserver.accesstokens;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;

public class AccessTokenCacheFetcherTest {

    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String TARGET = "//storage.googleapis.com/projects/_/buckets/example";
    private static final String ALICE = "alice@EXAMPLE.COM";

    private static SettingsOverride backupSettings;

    @BeforeClass
    public static void setupClass() {
        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.REMOTE_CACHE, "com.google.cloud.broker.caching.remote.RedisCache",
            AppSettings.PROVIDER_BACKEND, "com.google.cloud.broker.apps.brokerserver.accesstokens.providers.MockProvider",
            AppSettings.ACCESS_TOKEN_LOCAL_CACHE_TIME, "1234",
            AppSettings.ACCESS_TOKEN_REMOTE_CACHE_TIME, "6789"
        ));
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        // Restore settings
        backupSettings.restore();
    }

    @Test
    public void testComputeResult() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, GCS, TARGET);
        AccessToken token = (AccessToken) fetcher.computeResult();
        assertEquals(token.getValue(), "FakeAccessToken/Owner=" + ALICE.toLowerCase() + ";Scope=" + GCS + ";Target=" + TARGET);
        assertEquals(token.getExpiresAt(), 999999999L);
    }

    @Test
    public void testFromJSON() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, GCS, TARGET);
        String json = "{\"expiresAt\": 888888888, \"value\": \"blah\"}";
        AccessToken token;
        try {
            token = (AccessToken) fetcher.fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals(token.getValue(), "blah");
        assertEquals(token.getExpiresAt(), 888888888L);
    }

    @Test
    public void testGetCacheKey() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, GCS, TARGET);
        assertEquals(String.format("access-token-%s-%s-%s", ALICE, GCS, TARGET), fetcher.getCacheKey());
    }

    @Test
    public void testGetLocalCacheTime() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, GCS, TARGET);
        assertEquals(1234, fetcher.getLocalCacheTime());
    }

    @Test
    public void testGetRemoteCacheTime() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, GCS, TARGET);
        assertEquals(6789, fetcher.getRemoteCacheTime());
    }

}
