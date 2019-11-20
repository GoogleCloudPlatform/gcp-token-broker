/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.broker.authorization;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.database.backends.DummyDatabaseBackend;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.encryption.backends.DummyEncryptionBackend;
import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import com.google.common.base.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.kerby.util.NetworkUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

public class AuthorizerTest {
    static {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
    }
    private static Authorizer authorizer;
    private static int authorizerPort;

    private static SettingsOverride backupSettings;

    /**
     * set System property sun.security.krb5.debug=true to enable krb5 debug
     */
    @BeforeClass
    public static void setupClass() throws Exception {
        authorizerPort = NetworkUtil.getServerPort();

        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.AUTHORIZER_HOST, "localhost",
            AppSettings.AUTHORIZER_PORT, String.valueOf(authorizerPort),
            AppSettings.OAUTH_CLIENT_ID, "FakeClientId",
            AppSettings.OAUTH_CLIENT_SECRET, "FakeClientSecret",
            AppSettings.ENCRYPTION_BACKEND, DummyEncryptionBackend.class.getCanonicalName(),
            AppSettings.DATABASE_BACKEND, DummyDatabaseBackend.class.getCanonicalName()
        ));

        authorizer = new Authorizer();
        authorizer.start();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        authorizer.close();

        // Restore settings
        backupSettings.restore();
    }

    /**
     * Check that the user is correctly redirected to the Google login page.
     */
    @Test
    public void testGoogleRedirect() throws IOException {
        HttpGet req = new HttpGet("http://localhost:" + authorizerPort + "/google/login");
        HttpClient httpClient = HttpClientBuilder.create().disableRedirectHandling().build();
        HttpResponse response = httpClient.execute(req);
        int statusCode = response.getStatusLine().getStatusCode();
        // Should be redirected to Google OAuth page
        assertEquals(302, statusCode);
        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?access_type=offline&approval_prompt=force&client_id=FakeClientId&redirect_uri=http://localhost:" + authorizerPort + "/google/oauth2callback&response_type=code&scope=https://www.googleapis.com/auth/devstorage.read_write%20email%20profile",
            response.getHeaders("Location")[0].getValue());
    }

    @Test
    public void testRefreshTokenStore() {
        String token = "abcd";
        authorizer.servlet.saveRefreshToken("alice@example.com", token);

        RefreshToken refreshToken = (RefreshToken) AbstractDatabaseBackend.getInstance().get(RefreshToken.class, "alice@example.com");

        assertNotNull(refreshToken);
        byte[] decrypted = AbstractEncryptionBackend.getInstance()
            .decrypt(refreshToken.getValue());

        assertEquals(new String(decrypted, Charsets.UTF_8), token);
    }
}
