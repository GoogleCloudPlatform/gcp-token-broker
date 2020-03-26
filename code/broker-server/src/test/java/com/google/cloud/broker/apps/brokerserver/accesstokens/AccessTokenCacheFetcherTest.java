// Copyright 2020 Google LLC
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
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*"})
@PrepareForTest({AccessBoundaryUtils.class})  // Classes to be mocked
public class AccessTokenCacheFetcherTest {

    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");
    private static final String TARGET = "//storage.googleapis.com/projects/_/buckets/example";
    private static final String ALICE = "alice@EXAMPLE.COM";

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.REMOTE_CACHE, "com.google.cloud.broker.caching.remote.RedisCache",
        AppSettings.PROVIDER_BACKEND, "com.google.cloud.broker.apps.brokerserver.accesstokens.providers.MockProvider",
        AppSettings.USER_MAPPER, "com.google.cloud.broker.usermapping.MockUserMapper",
        AppSettings.ACCESS_TOKEN_LOCAL_CACHE_TIME, "1234",
        AppSettings.ACCESS_TOKEN_REMOTE_CACHE_TIME, "6789"
    ));

    @Test
    public void testComputeResult() {
        // Mock the Access Boundary API
        MockAccessBoundary.mock();

        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, SCOPES, TARGET);
        AccessToken token = (AccessToken) fetcher.computeResult();
        assertEquals(
            "FakeAccessToken/GoogleIdentity=alice@altostrat.com;Scopes=" + String.join(",", SCOPES) + ";Target=" + TARGET,
            token.getValue());
        assertEquals(token.getExpiresAt(), 999999999L);
    }

    @Test
    public void testFromJSON() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, SCOPES, TARGET);
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
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, SCOPES, TARGET);
        assertEquals(String.format("access-token-%s-%s-%s", ALICE, SCOPES, TARGET), fetcher.getCacheKey());
    }

    @Test
    public void testGetLocalCacheTime() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, SCOPES, TARGET);
        assertEquals(1234, fetcher.getLocalCacheTime());
    }

    @Test
    public void testGetRemoteCacheTime() {
        AccessTokenCacheFetcher fetcher = new AccessTokenCacheFetcher(ALICE, SCOPES, TARGET);
        assertEquals(6789, fetcher.getRemoteCacheTime());
    }

}
