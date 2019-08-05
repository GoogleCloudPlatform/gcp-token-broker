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

import javax.security.auth.login.LoginException;
import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

import com.sun.security.auth.module.Krb5LoginModule;
import org.ietf.jgss.GSSException;

import static org.junit.Assert.*;
import org.junit.Test;

import static com.google.cloud.broker.hadoop.fs.SpnegoUtils.newSPNEGOToken;


public class SpnegoUtilsTest {

    private static final String REALM = "EXAMPLE.COM";

    public static final String KERBEROS_ERROR = "No valid credentials provided (Mechanism level: No valid credentials provided (Mechanism level: Failed to find any Kerberos tgt))";

    private final Krb5LoginModule krb5LoginModule = new Krb5LoginModule();

    private Subject login(String username) {
        String alice = username + "@" + REALM;
        Subject subject = new Subject();

        final Map<String, String> options = new HashMap<String, String>();
        options.put("keyTab", "/etc/security/keytabs/" + username + ".keytab");
        options.put("principal", alice);
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");
        options.put("refreshKrb5Config", "true");
        options.put("renewTGT", "true");
        options.put("storeKey", "true");
        options.put("useKeyTab", "true");
        options.put("useTicketCache", "true");

        krb5LoginModule.initialize(subject, null,
            new HashMap<String, String>(),
            options);

        try {
            boolean loginOK = krb5LoginModule.login();
            assertTrue(loginOK);
            boolean commitOK = krb5LoginModule.commit();
            assertTrue(commitOK);
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
        return subject;
    }

    private void logout() {
        try {
            krb5LoginModule.logout();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLoggedIn() {
        Subject subject = login("alice");
        byte[] token = Subject.doAs(subject, (PrivilegedAction<byte[]>) () -> {
            try {
                return newSPNEGOToken("broker", "testhost", "EXAMPLE.COM");
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(token.length > 0);
        logout();
    }

    @Test
    public void testNotLoggedIn() {
        Subject subject = new Subject();
        Subject.doAs(subject, (PrivilegedAction<Void>) () -> {
            try {
                newSPNEGOToken("broker", "testhost", "EXAMPLE.COM");
                fail();
            } catch (GSSException e) {
                assertEquals(KERBEROS_ERROR, e.getMessage());
            }
            return null;
        });
    }

}
