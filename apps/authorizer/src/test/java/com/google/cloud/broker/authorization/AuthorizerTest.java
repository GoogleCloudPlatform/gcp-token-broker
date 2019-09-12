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
import com.google.cloud.broker.database.backends.JDBCBackend;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.encryption.backends.DummyEncryptionBackend;
import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.settings.AppSettings;
import com.google.common.base.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.kerby.util.NetworkUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AuthorizerTest extends KdcTestBase {
    static {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
    }
    private static Authorizer authorizer;
    private static HttpClient httpClient;
    private static int authorizerPort;
    /**
     * set System property sun.security.krb5.debug=true to enable krb5 debug
     */
    @BeforeClass
    public static void setup() throws Exception {
        authorizerPort = NetworkUtil.getServerPort();

        AppSettings.reset();
        AppSettings.setProperty("AUTHORIZER_HOST", "localhost");
        AppSettings.setProperty("AUTHORIZER_PORT", String.valueOf(authorizerPort));
        AppSettings.setProperty("OAUTH_CALLBACK_URI", "http://localhost:8080/oauth2callback");
        AppSettings.setProperty("AUTHORIZER_PRINCIPAL", serverPrincipal);
        AppSettings.setProperty("AUTHORIZER_KEYTAB", serverKeytab.toString());
        AppSettings.setProperty("OAUTH_CLIENT_ID", "REQUIRED");
        AppSettings.setProperty("OAUTH_CLIENT_SECRET", "REQUIRED");
        AppSettings.setProperty("AUTHORIZER_ENABLE_SPNEGO", "true");
        AppSettings.setProperty("ENCRYPTION_BACKEND", DummyEncryptionBackend.class.getCanonicalName());
        AppSettings.setProperty("DATABASE_BACKEND", JDBCBackend.class.getCanonicalName());
        AppSettings.setProperty("DATABASE_JDBC_URL", "jdbc:h2:mem:;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");

        authorizer = new Authorizer();
        authorizer.start();
        httpClient = Spnego.httpClient(clientKeytab.toString(), clientPrincipal, serverPrincipal);
    }

    @AfterClass
    public static void teardown() throws Exception {
        authorizer.close();
    }

    /**
     *
     */
    @Test
    public void testSpnego() throws IOException {
        HttpGet req = new HttpGet("http://localhost:" + authorizerPort);
        req.setHeader(Spnego.SpnegoLoginService.REALM_HEADER, realm);
        HttpResponse response = httpClient.execute(req);
        int statusCode = response.getStatusLine().getStatusCode();
        // Should be redirected to Google OAuth page
        assertEquals(statusCode, 302);
    }

    public void testRefreshTokenStore() {
        String token = "abcd";
        authorizer.getCallbackServlet().putRefreshToken(clientPrincipal, token);

        RefreshToken refreshToken = (RefreshToken) AbstractDatabaseBackend.getInstance().get(RefreshToken.class, clientPrincipal);

        assertNotNull(refreshToken);
        byte[] decrypted = AbstractEncryptionBackend.getInstance()
            .decrypt((byte[])refreshToken.getValue(RefreshToken.VALUE));

        assertEquals(new String(decrypted, Charsets.UTF_8), token);
    }
}
