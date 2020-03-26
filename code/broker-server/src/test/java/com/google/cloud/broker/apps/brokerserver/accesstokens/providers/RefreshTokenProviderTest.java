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
import org.junit.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.backends.DummyDatabaseBackend;


public class RefreshTokenProviderTest {

    // TODO: Still needs tests:
    // - Happy path.

    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.DATABASE_BACKEND, "com.google.cloud.broker.database.backends.DummyDatabaseBackend",
        AppSettings.USER_MAPPER, "com.google.cloud.broker.usermapping.MockUserMapper"
    ));

    @After
    public void teardown() {
        // Clear the database
        ConcurrentMap<String, Object> map = DummyDatabaseBackend.getMap();
        map.clear();
    }

    @Test
    public void testUnauthorized() {
        RefreshTokenProvider provider = new RefreshTokenProvider();
        try {
            provider.getAccessToken("bob@example.com", SCOPES);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals(
                "GCP Token Broker authorization is invalid or has expired for identity: bob@example.com",
                e.getStatus().getDescription());
        }
    }

}
