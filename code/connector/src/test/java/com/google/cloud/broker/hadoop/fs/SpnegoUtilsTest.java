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

package com.google.cloud.broker.hadoop.fs;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;

import org.ietf.jgss.*;

import static org.junit.Assert.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.broker.testing.FakeKDC;
import static com.google.cloud.broker.credentials.SpnegoUtils.newSPNEGOToken;
import static com.google.cloud.broker.hadoop.fs.TestingTools.*;


public class SpnegoUtilsTest {

    private static FakeKDC fakeKDC;
    static final String TGT_ERROR = "No valid credentials provided (Mechanism level: No valid credentials provided (Mechanism level: Failed to find any Kerberos tgt))";

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

    /**
     * Check the happy path: a user logs in and can generate a SPNEGO token.
     */
    @Test
    public void testLoggedIn() {
        // Let a logged-in user generate a token
        Subject alice = fakeKDC.login(ALICE);
        byte[] spnegoToken = Subject.doAs(alice, (PrivilegedAction<byte[]>) () -> {
            try {
                return newSPNEGOToken(BROKER);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
        });

        // Let the broker decrypt the token and verify the user's identity
        Subject broker = fakeKDC.login(BROKER);
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
                newSPNEGOToken(BROKER);
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
        Subject alice = fakeKDC.login(ALICE);
        Subject.doAs(alice, (PrivilegedAction<byte[]>) () -> {
            try {
                newSPNEGOToken("blah/foo@BAR");
                fail();
            } catch (Exception e) {
                assertEquals(GSSException.class, e.getClass());
            }
            return null;
        });
    }

}
