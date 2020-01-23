package com.google.cloud.broker.apps.brokerserver.validation;

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

    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final String HIVE = "hive/testhost@EXAMPLE.COM";
    private static final String PRESTO = "presto/testhost@EXAMPLE.COM";
    private static final String SPARK = "spark/testhost@EXAMPLE.COM";

    private SettingsOverride backupSettings;

    @Before
    public void setup() {
        // Override settings
        Object scopesWhitelist = ConfigFactory.parseString(
            AppSettings.SCOPES_WHITELIST + "=[\"" + GCS + "\", \"" + BIGQUERY + "\"]"
        ).getAnyRef(AppSettings.SCOPES_WHITELIST);
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.PROXY_USER_WHITELIST, HIVE + "," + PRESTO,
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
        Validation.validateParameterNotEmpty("my-param", "Request must provide the `%s` parameter");
        try {
            Validation.validateParameterNotEmpty("my-param", "");
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.INVALID_ARGUMENT.getCode(), e.getStatus().getCode());
            assertEquals("Request must provide the `my-param` parameter", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateImpersonator() {
        Validation.validateImpersonator(ALICE, ALICE);
        Validation.validateImpersonator(HIVE, ALICE);
        Validation.validateImpersonator(PRESTO, ALICE);
        try {
            Validation.validateImpersonator(SPARK, ALICE);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("spark/testhost@EXAMPLE.COM is not a whitelisted impersonator", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateScope() {
        Validation.validateScope(GCS);
        Validation.validateScope(BIGQUERY);
        Validation.validateScope(GCS + "," + BIGQUERY);
        Validation.validateScope(BIGQUERY + "," + GCS);
        try {
            Validation.validateScope(BIGTABLE);
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("https://www.googleapis.com/auth/bigtable.data.readonly is not a whitelisted scope", e.getStatus().getDescription());
        }
    }

}