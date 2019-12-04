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
import java.util.Map;

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

    private static byte[] generateToken() {
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
            return token;
        } catch (GSSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check that an exception is thrown if the keytab doesn't exist.
     */
    @Test
    public void testInexistentKeytabPath() throws Exception {
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"keytab\": \"/home/does-not-exist\", \"principal\": \"blah\"}]"))) {
            try {
                SpnegoAuthenticator auth = new SpnegoAuthenticator();
                auth.authenticateUser();
                fail();
            } catch (RuntimeException e) {
                assertEquals(RuntimeException.class, e.getClass());
                assertEquals("Failed login for principal `blah` with keytab `/home/does-not-exist`", e.getMessage());
            }
        }
    }


    /**
     * Check that an exception is thrown if the JSON setting is misformatted.
     */
    @Test
    public void testMisformattedJSONSetting() throws Exception {
        try(SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"foo\": \"bar\"}]"))) {
            try {
                SpnegoAuthenticator auth = new SpnegoAuthenticator();
                auth.authenticateUser();
                fail();
            } catch (Exception e) {
                assertEquals(IllegalArgumentException.class, e.getClass());
                assertEquals("Invalid `KEYTABS` setting", e.getMessage());
            }
        }
    }

    public void testInvalidKeytab() throws Exception {
        Path fakeKeytab = Files.createTempFile("fake", "keytab");
        try(SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"keytab\": \"" + fakeKeytab.toString() + "\", \"principal\": \"blah\"}]"))) {
            try {
                SpnegoAuthenticator auth = new SpnegoAuthenticator();
                auth.authenticateUser();
                fail();
            } catch (IllegalStateException e) {
                assertEquals("Failed login for principal `blah` with keytab `" + fakeKeytab.toString() + "`", e.getMessage());
            }
        }
    }

    @Test
    public void testHeaderDoesntStartWithNegotiate() throws Exception {
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"keytab\": \"" + fakeKDC.getKeytabPath(BROKER) + "\", \"principal\": \"" + BROKER + "\"}]"))) {

            SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
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
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"keytab\": \"" + fakeKDC.getKeytabPath(BROKER) + "\", \"principal\": \"" + BROKER + "\"}]"))) {
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
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.KEYTABS, "[{\"keytab\": \"" + fakeKDC.getKeytabPath(BROKER) + "\", \"principal\": \"" + BROKER + "\"}]"))) {
            // Let Alice generate a token
            Subject alice = fakeKDC.login("alice");
            byte[] token = Subject.doAs(alice, (PrivilegedAction<byte[]>) () -> {
                return generateToken();
            });
            // Let the SpnegoAuthenticator decrypt the token and authenticate Alice
            String encodedToken = Base64.getEncoder().encodeToString(token);
            SpnegoAuthenticator auth = new SpnegoAuthenticator();
            String authenticateUser = auth.authenticateUser("Negotiate " + encodedToken);
            assertEquals("alice@EXAMPLE.COM", authenticateUser);
        }
    }
}
