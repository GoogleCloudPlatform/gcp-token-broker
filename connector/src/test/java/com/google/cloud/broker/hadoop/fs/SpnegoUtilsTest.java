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

package com.google.cloud.broker.hadoop.fs;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

import com.sun.security.auth.module.Krb5LoginModule;
import org.ietf.jgss.*;

import static org.junit.Assert.*;
import org.junit.Test;

import static com.google.cloud.broker.hadoop.fs.SpnegoUtils.newSPNEGOToken;


public class SpnegoUtilsTest {

    private static final String REALM = "EXAMPLE.COM";
    private static final String BROKER_HOST = "testhost";
    private static final String BROKER_NAME = "broker";

    public static final String TGT_ERROR = "No valid credentials provided (Mechanism level: No valid credentials provided (Mechanism level: Failed to find any Kerberos tgt))";
    public static final String SERVER_NOT_FOUND_ERROR = "No valid credentials provided (Mechanism level: No valid credentials provided (Mechanism level: Server not found in Kerberos database (7) - LOOKING_UP_SERVER))";

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

    public static String decryptToken(byte[] token) {
        try {
            GSSManager manager = GSSManager.getInstance();
            Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
            GSSCredential serverCreds = manager.createCredential(null,
                GSSCredential.DEFAULT_LIFETIME, spnegoOid, GSSCredential.ACCEPT_ONLY);
            GSSContext context = manager.createContext((GSSCredential)serverCreds);
            context.acceptSecContext(token, 0, token.length);
            return context.getSrcName().toString();
        } catch (GSSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check the happy path: a user logs in and can generate a SPNEGO token.
     */
    @Test
    public void testLoggedIn() {
        // Let a logged-in user generate a token
        Subject alice = login("alice");
        byte[] spnegoToken = Subject.doAs(alice, (PrivilegedAction<byte[]>) () -> {
            try {
                return newSPNEGOToken(BROKER_NAME, BROKER_HOST, REALM);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
        });

        // Let the broker decrypt the token and verify the user's identity
        Subject broker = login("broker");
        String decrypted = Subject.doAs(broker, (PrivilegedAction<String>) () ->
            decryptToken(spnegoToken)
        );
        assertEquals("alice@EXAMPLE.COM", decrypted);
    }

    /**
     * Check that a GSSException is thrown if the user is not logged in and attempts to generate a SPNEGO token.
     */
    @Test
    public void testNotLoggedIn() {
        Subject anonymous = new Subject();
        Subject.doAs(anonymous, (PrivilegedAction<Void>) () -> {
            try {
                newSPNEGOToken(BROKER_NAME, BROKER_HOST, "EXAMPLE.COM");
                fail();
            } catch (GSSException e) {
                assertEquals(TGT_ERROR, e.getMessage());
            }
            return null;
        });
    }

    /**
     * Check that a GSSException is thrown if the provided broker principal is wrong.
     */
    @Test
    public void testWrongBrokerPrincipal() {
        Subject alice = login("alice");
        Subject.doAs(alice, (PrivilegedAction<byte[]>) () -> {
            try {
                newSPNEGOToken("wrong", BROKER_HOST, REALM);
                fail();
            } catch (GSSException e) {
                assertEquals(SERVER_NOT_FOUND_ERROR, e.getMessage());
            }
            return null;
        });
    }


}
