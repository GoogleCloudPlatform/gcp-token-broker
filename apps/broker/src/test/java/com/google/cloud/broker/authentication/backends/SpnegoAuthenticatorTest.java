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

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.AppSettings;


public class SpnegoAuthenticatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setup() {
        AbstractAuthenticationBackend.reset();
        AppSettings.getInstance().reset();
        AppSettings.getInstance().setProperty("AUTHENTICATION_BACKEND", "com.google.cloud.broker.authentication.backends.SpnegoAuthenticator");
    }

    /**
     * Check that an exception is thrown if the provided KEYTABS_PATH doesn't contain any files.
     */
    @Test
    public void testEmptyKeytabPath() {
        System.out.println("XXX testEmptyKeytabPath");
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
        System.out.println("XXX testInexistentKeytabPath");
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
        System.out.println("XXX testInvalidKeytab");
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
        System.out.println("XXX testHeaderDoesntStartWithNegotiate");
        AppSettings.getInstance().setProperty("KEYTABS_PATH", "/etc/security/keytabs/broker/");
        AppSettings.getInstance().setProperty("BROKER_SERVICE_NAME", "broker");
        AppSettings.getInstance().setProperty("BROKER_SERVICE_HOSTNAME", "testhost");


        SpnegoAuthenticator auth = (SpnegoAuthenticator) AbstractAuthenticationBackend.getInstance();
        try {
            auth.authenticateUser("xxx");
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
            assertEquals("UNAUTHENTICATED: Use \"authorization: Negotiate <token>\" metadata to authenticate", e.getMessage());
        }
    }

    // TODO: token doesn't start with "Negotiate"
}
