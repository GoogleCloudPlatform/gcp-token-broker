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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.database.backends.DummyDatabaseBackend;
import com.google.common.base.CharMatcher;
import org.junit.*;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;

public class HybridProviderTest {

    // TODO: Still needs tests:
    // - Happy path.

    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");
    private final static String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.USER_MAPPER, "com.google.cloud.broker.usermapping.MockUserMapper",
        AppSettings.HYBRID_USER_PROVIDER, "com.google.cloud.broker.apps.brokerserver.accesstokens.providers.MockProvider"
    ));

    @After
    public void teardown() {
        // Clear the database
        DummyDatabaseBackend.getCache().clear();
    }

    @Test
    public void testUser() {
        HybridProvider provider = new HybridProvider();
        AccessToken accessToken = provider.getAccessToken("alice@example.com", SCOPES);
        assertEquals(
            "FakeAccessToken/GoogleIdentity=alice@example.com;Scopes=" + String.join(",", SCOPES),
            accessToken.getValue());
    }

    @Test
    public void testServiceAccount() {
        HybridProvider provider = new HybridProvider();
        AccessToken accessToken = provider.getAccessToken("alice-shadow@" + projectId + ".iam.gserviceaccount.com", SCOPES);
        assertTrue(accessToken.getValue().startsWith("y"));
        assertEquals(2, CharMatcher.is('.').countIn(accessToken.getValue()));
        assertTrue(accessToken.getExpiresAt() > 0);
    }

}
