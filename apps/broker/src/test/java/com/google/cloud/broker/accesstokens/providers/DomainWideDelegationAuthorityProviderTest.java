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

package com.google.cloud.broker.accesstokens.providers;

import static org.junit.Assert.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.ClassRule;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.settings.AppSettings;

public class DomainWideDelegationAuthorityProviderTest {

    private static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";

    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    public static void setupClass() {
        AppSettings.reset();
        environmentVariables.set("APP_SETTING_DOMAIN_NAME", "example.com");
        environmentVariables.set("APP_SETTING_JWT_LIFE", "30");
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
            provider.getAccessToken("bob@EXAMPLE.COM", SCOPE);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
        }
    }

    // TODO: Test the happy path.
}
