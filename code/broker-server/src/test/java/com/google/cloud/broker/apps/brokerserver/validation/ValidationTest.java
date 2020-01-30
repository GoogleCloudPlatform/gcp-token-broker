package com.google.cloud.broker.apps.brokerserver.validation;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;

public class ValidationTest {

    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String BIGQUERY = "https://www.googleapis.com/auth/bigquery";
    private static final String BIGTABLE = "https://www.googleapis.com/auth/bigtable.data.readonly";

    private SettingsOverride backupSettings;

    @Before
    public void setup() {
        // Override settings
        Object scopesWhitelist = ConfigFactory.parseString(
            AppSettings.SCOPES_WHITELIST + "=[\"" + GCS + "\", \"" + BIGQUERY + "\"]"
        ).getAnyRef(AppSettings.SCOPES_WHITELIST);
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.SCOPES_WHITELIST, scopesWhitelist
        ));
    }

    @After
    public void tearDown() throws Exception {
        // Restore settings
        backupSettings.restore();
    }

    @Test
    public void testrequireProperty() {
        Validation.validateParameterNotEmpty("my-param", "Request must provide `%s`");
        try {
            Validation.validateParameterNotEmpty("my-param", "");
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.INVALID_ARGUMENT.getCode(), e.getStatus().getCode());
            assertEquals("Request must provide `my-param`", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateScope() {
        Validation.validateScopes(List.of(GCS));
        Validation.validateScopes(List.of(BIGQUERY));
        Validation.validateScopes(List.of(GCS, BIGQUERY));
        Validation.validateScopes(List.of(BIGQUERY, GCS));
        try {
            Validation.validateScopes(List.of(BIGTABLE));
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("`[https://www.googleapis.com/auth/bigtable.data.readonly]` are not whitelisted scopes", e.getStatus().getDescription());
        }
    }

}