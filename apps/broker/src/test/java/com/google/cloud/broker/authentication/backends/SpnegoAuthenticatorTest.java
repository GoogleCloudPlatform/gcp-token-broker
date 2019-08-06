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
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.sun.security.auth.module.Krb5LoginModule;
import org.ietf.jgss.*;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.AppSettings;


public class SpnegoAuthenticatorTest {

    private static final String REALM = "EXAMPLE.COM";
    private static final String BROKER_HOST = "testhost";
    private static final String BROKER_NAME = "broker";
    private static final String KEYTABS_PATH = "/etc/security/keytabs/broker/";
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setup() {
        AbstractAuthenticationBackend.reset();
        AppSettings.getInstance().reset();
        AppSettings.getInstance().setProperty("AUTHENTICATION_BACKEND", "com.google.cloud.broker.authentication.backends.SpnegoAuthenticator");
    }

    public static Subject login(String user) {
        Krb5LoginModule krb5LoginModule = new Krb5LoginModule();

        String principal;
        String keytab;
        if (user == "broker") {
            principal = BROKER_NAME + "/" + BROKER_HOST + "@" + REALM;
            keytab = "/etc/security/keytabs/broker/broker.keytab";
        }
        else {
            principal = user + "@" + REALM;
            keytab = "/etc/security/keytabs/users/" + user + ".keytab";
        }

        final Map<String, String> options = new HashMap<String, String>();
        options.put("keyTab", keytab);
        options.put("principal", principal);
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");
        options.put("refreshKrb5Config", "true");
        options.put("renewTGT", "true");
        options.put("storeKey", "true");
        options.put("useKeyTab", "true");
        options.put("useTicketCache", "true");

        Subject subject = new Subject();
        krb5LoginModule.initialize(subject, null,
            new HashMap<String, String>(),
            options);

        try {
            krb5LoginModule.login();
            krb5LoginModule.commit();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
        return subject;
    }

    public static byte[] generateToken() {
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
            String servicePrincipal = BROKER_NAME + "/" + BROKER_HOST + "@" + REALM;
            GSSName gssServerName = manager.createName(servicePrincipal , krb5PrincipalNameType, krb5Mechanism);
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
     * Check that an exception is thrown if the provided KEYTABS_PATH doesn't contain any files.
     */
    @Test
    public void testEmptyKeytabPath() {
        File emptyFolder;
        try {
            emptyFolder = tmp.newFolder("empty");
            AppSettings.getInstance().setProperty("KEYTABS_PATH", emptyFolder.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
            auth.authenticateUser();
            fail();
        } catch (IllegalStateException e) {
            assertEquals("No valid keytabs found in path `" + emptyFolder.getAbsolutePath() + "` as defined in the `KEYTABS_PATH` setting", e.getMessage());
        }
    }

    /**
     * Check that an exception is thrown if the KEYTABS_PATH doesn't exist.
     */
    @Test
    public void testInexistentKeytabPath() {
        AppSettings.getInstance().setProperty("KEYTABS_PATH", "/home/does-not-exist");
        try {
            SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
            auth.authenticateUser();
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Invalid path `/home/does-not-exist` as defined in the `KEYTABS_PATH` setting", e.getMessage());
        }
    }

    /**
     * Check that an exception is thrown if the KEYTABS_PATH doesn't contain any valid keytabs.
     */
    @Test
    public void testInvalidKeytab() {
        File folder;
        try {
            folder = tmp.newFolder("folder");
            tmp.newFile("folder/fake.keytab");
            AppSettings.getInstance().setProperty("KEYTABS_PATH", folder.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
            auth.authenticateUser();
            fail();
        } catch (IllegalStateException e) {
            assertEquals("No valid keytabs found in path `" + folder.getAbsolutePath() + "` as defined in the `KEYTABS_PATH` setting", e.getMessage());
        }
    }

    @Test
    public void testHeaderDoesntStartWithNegotiate() {
        AppSettings.getInstance().setProperty("KEYTABS_PATH", KEYTABS_PATH);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_NAME", BROKER_NAME);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_HOSTNAME", BROKER_HOST);

        SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
        try {
            auth.authenticateUser("xxx");
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
            assertEquals("UNAUTHENTICATED: Use \"authorization: Negotiate <token>\" metadata to authenticate", e.getMessage());
        }
    }

    @Test
    public void testInvalidSpnegoToken() {
        AppSettings.getInstance().setProperty("KEYTABS_PATH", KEYTABS_PATH);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_NAME", BROKER_NAME);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_HOSTNAME", BROKER_HOST);

        SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
        try {
            auth.authenticateUser("Negotiate xxx");
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
            assertEquals("UNAUTHENTICATED: SPNEGO authentication failed", e.getMessage());
        }
    }

    /**
     * Check the happy path: User generates a SPNEGO token, then the broker decrypts it to authenticate the user.
     */
    @Test
    public void testSuccess() {
        AppSettings.getInstance().setProperty("KEYTABS_PATH", KEYTABS_PATH);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_NAME", BROKER_NAME);
        AppSettings.getInstance().setProperty("BROKER_SERVICE_HOSTNAME", BROKER_HOST);

        // Let Alice generate a token
        Subject broker = login("alice");
        byte[] token = Subject.doAs(broker, (PrivilegedAction<byte[]>) () -> {
            return generateToken();
        });

        // Let the SpnegoAuthenticator decrypt the token and authenticate Alice
        String encodedToken = Base64.getEncoder().encodeToString(token);
        SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
        String authenticateUser =auth.authenticateUser("Negotiate " + encodedToken);
        assertEquals("alice@EXAMPLE.COM", authenticateUser);
    }
}
