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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;

public class DomainWideDelegationAuthorityProviderTest {

    // TODO: Still needs tests:
    // - Happy path

    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");

    private static SettingsOverride backupSettings;

    @BeforeClass
    public static void setupClass() {
        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.GSUITE_DOMAIN, "example.com"
        ));
    }

    @AfterClass
    public static void teardDownClass() throws Exception {
        // Restore settings
        backupSettings.restore();
    }

    @Test
    public void testGoogleIdentity() {
        DomainWideDelegationAuthorityProvider provider = new DomainWideDelegationAuthorityProvider();
        assertEquals("alice@example.com", provider.getGoogleIdentity("alice@EXAMPLE.COM"));
        assertEquals("alice@example.com", provider.getGoogleIdentity("alice@EXAMPLE.NET"));
        assertEquals("alice@example.com", provider.getGoogleIdentity("alice"));
        try {
            provider.getGoogleIdentity("");
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {}

        try {
            provider.getGoogleIdentity("@EXAMPLE.NET");
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {}

        try {
            provider.getGoogleIdentity("@");
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testUnauthorized() {
        DomainWideDelegationAuthorityProvider provider = new DomainWideDelegationAuthorityProvider();
        try {
            provider.getAccessToken("bob@EXAMPLE.com", SCOPES);
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
        }
    }

}
