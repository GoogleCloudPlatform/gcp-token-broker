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

package com.google.cloud.broker.apps.brokerserver.validation;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.*;

import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.validation.EmailValidation;

public class ValidationTest {

    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String BIGQUERY = "https://www.googleapis.com/auth/bigquery";
    private static final String BIGTABLE = "https://www.googleapis.com/auth/bigtable.data.readonly";
    private static final Object scopesWhitelist = ConfigFactory.parseString(
        AppSettings.SCOPES_WHITELIST + "=[\"" + GCS + "\", \"" + BIGQUERY + "\"]"
    ).getAnyRef(AppSettings.SCOPES_WHITELIST);

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.SCOPES_WHITELIST, scopesWhitelist
    ));

    @Test
    public void testrequireProperty() {
        GrpcRequestValidation.validateParameterNotEmpty("my-param", "Request must provide `%s`");
        try {
            GrpcRequestValidation.validateParameterNotEmpty("my-param", "");
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.INVALID_ARGUMENT.getCode(), e.getStatus().getCode());
            assertEquals("Request must provide `my-param`", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateScope() {
        ScopeValidation.validateScopes(List.of(GCS));
        ScopeValidation.validateScopes(List.of(BIGQUERY));
        ScopeValidation.validateScopes(List.of(GCS, BIGQUERY));
        ScopeValidation.validateScopes(List.of(BIGQUERY, GCS));
        try {
            ScopeValidation.validateScopes(List.of(BIGTABLE));
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("`[https://www.googleapis.com/auth/bigtable.data.readonly]` are not whitelisted scopes", e.getStatus().getDescription());
        }
    }

    @Test
    public void validateEmail() {
        EmailValidation.validateEmail("alice@example.com");
        EmailValidation.validateEmail("alice-shadow@my-project.iam.gserviceaccount.com");
        for (String value : new String[]{"alice", "alice@", "@example.com", "xxx()@xxx"})
        try {
            EmailValidation.validateEmail(value);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

}