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

package com.google.cloud.broker.authentication.backends;

import javax.security.auth.Subject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import org.ietf.jgss.*;
import static org.junit.Assert.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.testing.FakeKDC;


public class SpnegoAuthenticatorTest {

    private static FakeKDC fakeKDC;
    private static final String REALM = "EXAMPLE.COM";
    private static final String BROKER_HOST = "testhost";
    private static final String BROKER = "broker/" + BROKER_HOST + "@" + REALM;
    private static final String ALICE = "alice@" + REALM;

    @BeforeClass
    public static void setUpClass() {
        fakeKDC = new FakeKDC(REALM);
        fakeKDC.start();
        fakeKDC.createPrincipal(ALICE);
        fakeKDC.createPrincipal(BROKER);
    }

    @AfterClass
    public static void tearDownClass() {
        fakeKDC.stop();
    }

    private static String generateSpnegoToken(String principal) {
        Subject subject = fakeKDC.login(principal);
        return Subject.doAs(subject, (PrivilegedAction<String>) () -> {
            String SPNEGO_OID = "1.3.6.1.5.5.2";
            String KRB5_MECHANISM_OID = "1.2.840.113554.1.2.2";
            String KRB5_PRINCIPAL_NAME_OID = "1.2.840.113554.1.2.2.1";
            byte[] token;
            try {
                // Create GSS context for the broker service and the logged-in user
                Oid krb5Mechanism = new Oid(KRB5_MECHANISM_OID);
                Oid krb5PrincipalNameType = new Oid(KRB5_PRINCIPAL_NAME_OID);
                Oid spnegoOid = new Oid(SPNEGO_OID);
                GSSManager manager = GSSManager.getInstance();
                GSSName gssServerName = manager.createName(BROKER, krb5PrincipalNameType, krb5Mechanism);
                GSSContext gssContext = manager.createContext(
                    gssServerName, spnegoOid, null, GSSCredential.DEFAULT_LIFETIME);
                gssContext.requestMutualAuth(true);
                gssContext.requestCredDeleg(true);

                // Generate the SPNEGO token
                token = new byte[0];
                token = gssContext.initSecContext(token, 0, token.length);
                return Base64.getEncoder().encodeToString(token);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Check that an exception is thrown if the keytab doesn't exist.
     */
    @Test
    public void testInexistentKeytabPath() throws Exception {
        List<Map<String, String>> config = List.of(Map.of(
            "keytab", "/home/does-not-exist",
            "principal", "blah"
        ));
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
            try {
                SpnegoAuthenticator auth = new SpnegoAuthenticator();
                auth.authenticateUser();
                fail();
            } catch (IllegalArgumentException e) {
                assertEquals("Keytab `/home/does-not-exist` in `authentication.spnego.keytabs` setting does not exist", e.getMessage());
            }
        }
    }

    /**
     * Check that an exception is thrown if a value is missing in the setting.
     */
    @Test
    public void testMissingValue() throws Exception {
        List<List<Map<String, String>>> configs = List.of(
            List.of(Map.of("keytab", "bar")),    // Missing principal
            List.of(Map.of("principal", "bar"))  // Missing keytab
        );
        for (List<Map<String, String>> config: configs) {
            try(SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
                try {
                    SpnegoAuthenticator auth = new SpnegoAuthenticator();
                    auth.authenticateUser();
                    fail();
                } catch (IllegalArgumentException e) {
                    assertTrue(e.getMessage().startsWith("Invalid `authentication.spnego.keytabs` setting -- Error: hardcoded value: No configuration setting found for key"));
                }
            }
        }
    }

    @Test
    public void testInvalidKeytab() throws Exception {
        Path fakeKeytab = Files.createTempFile("fake", ".keytab");
        List<Map<String, String>> config = List.of(Map.of(
            "keytab", fakeKeytab.toString(),
            "principal", "blah"
        ));
        try(SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
            try {
                String token = generateSpnegoToken("alice");
                SpnegoAuthenticator auth = new SpnegoAuthenticator();
                auth.authenticateUser("Negotiate " + token);
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
                assertEquals("UNAUTHENTICATED: SPNEGO authentication failed", e.getMessage());
            }
        }
    }

    @Test
    public void testHeaderDoesntStartWithNegotiate() throws Exception {
        List<Map<String, String>> config = List.of(Map.of(
            "keytab", fakeKDC.getKeytabPath(BROKER).toString(),
            "principal", BROKER
        ));
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
            SpnegoAuthenticator auth = new SpnegoAuthenticator();
            try {
                auth.authenticateUser("xxx");
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
                assertEquals("UNAUTHENTICATED: Use \"authorization: Negotiate <token>\" metadata to authenticate", e.getMessage());
            }
        }
    }

    @Test
    public void testInvalidSpnegoToken() throws Exception {
        List<Map<String, String>> config = List.of(Map.of(
            "keytab", fakeKDC.getKeytabPath(BROKER).toString(),
            "principal", BROKER
        ));
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
            SpnegoAuthenticator auth = new SpnegoAuthenticator();
            try {
                auth.authenticateUser("Negotiate xxx");
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
                assertEquals("UNAUTHENTICATED: SPNEGO authentication failed", e.getMessage());
            }
        }
    }

    /**
     * Check the happy path: User generates a SPNEGO token, then the broker decrypts it to authenticate the user.
     */
    @Test
    public void testSuccess() throws Exception {
        List<Map<String, String>> config = List.of(Map.of(
            "keytab", fakeKDC.getKeytabPath(BROKER).toString(),
            "principal", BROKER
        ));
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, config))) {
            String token = generateSpnegoToken("alice");
            SpnegoAuthenticator auth = new SpnegoAuthenticator();
            String authenticateUser = auth.authenticateUser("Negotiate " + token);
            assertEquals("alice@EXAMPLE.COM", authenticateUser);
        }
    }
}
