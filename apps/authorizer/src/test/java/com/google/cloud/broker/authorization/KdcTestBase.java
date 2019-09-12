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

import com.google.common.io.Files;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.server.KdcConfig;
import org.apache.kerby.kerberos.kerb.server.KdcConfigKey;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.apache.kerby.kerberos.kerb.type.ticket.TgtTicket;
import org.conscrypt.Conscrypt;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class KdcTestBase {
    static {
        try {
            if (!Security.getProviders()[0].getName().equals("Conscrypt")) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            }
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Unable to configure Conscrypt JCE Provider", e);
        }
    }

    protected static final String realm = "EXAMPLE.COM";
    protected static final String hostname = "localhost";
    protected static final String clientPrincipal = "user@" + realm;
    protected static final String serverPrincipal = "HTTP/localhost@" + realm;
    protected static final String clientPassword = "changeit123";
    protected static final String serverPassword = "changeit1234";

    protected static SimpleKdcServer kdcServer;
    protected static KrbClient krbClient;
    protected static Path testDir;

    protected static Path ccFile;
    protected static Path serverKeytab;
    protected static Path clientKeytab;

    protected static Map<String, Path> TEST_KEYTABS = new HashMap<>();
    protected static Map<String, Subject> TEST_SUBJECTS = new HashMap<>();

    public static void preparePrincipals(List<String> principals) {
        try {
            for (String prin : principals) {
                TEST_KEYTABS.put(prin, testDir.resolve(prin + ".keytab"));
                kdcServer.createPrincipal(prin, clientPassword);
                TgtTicket tgt = krbClient.requestTgt(prin, clientPassword);
                SgtTicket tkt = krbClient.requestSgt(tgt, serverPrincipal);
                krbClient.storeTicket(tgt, ccFile.toFile());
                krbClient.storeTicket(tkt, ccFile.toFile());
                kdcServer.exportPrincipal(prin, TEST_KEYTABS.get(prin).toFile());
                TEST_SUBJECTS.put(prin, Spnego.login(prin, TEST_KEYTABS.get(prin).toString(), null, true));
            }
        } catch (KrbException|LoginException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        testDir = Files.createTempDir().toPath();

        kdcServer = new TestKdcServer(realm, hostname);
        kdcServer.setWorkDir(testDir.toFile());
        KdcConfig cfg = kdcServer.getKdcConfig();
        // For some reason kerby throws KDC_ERR_ETYPE_NOSUPP when trying to use aes256
        cfg.setString(KdcConfigKey.ENCRYPTION_TYPES, "aes128-cts-hmac-sha1-96");
        kdcServer.init();
        kdcServer.start();
        krbClient = kdcServer.getKrbClient();

        kdcServer.createPrincipal(serverPrincipal, serverPassword);
        kdcServer.createPrincipal(clientPrincipal, clientPassword);

        kinit();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        kdcServer.getKadmin().deleteBuiltinPrincipals();
        kdcServer.deletePrincipals(serverPrincipal);
        kdcServer.deletePrincipal(clientPrincipal);
        kdcServer.stop();
        testDir.toFile().delete();
    }

    public static void kinit() {
        ccFile = testDir.resolve("krb5cc_user");
        serverKeytab = testDir.resolve("server.keytab");
        clientKeytab = testDir.resolve("user.keytab");

        try {
            java.nio.file.Files.deleteIfExists(serverKeytab);
            java.nio.file.Files.deleteIfExists(clientKeytab);
            java.nio.file.Files.deleteIfExists(ccFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            TgtTicket tgt = krbClient.requestTgt(clientPrincipal, clientPassword);
            SgtTicket tkt = krbClient.requestSgt(tgt, serverPrincipal);
            krbClient.storeTicket(tgt, ccFile.toFile());
            krbClient.storeTicket(tkt, ccFile.toFile());
            kdcServer.exportPrincipal(serverPrincipal, serverKeytab.toFile());
            kdcServer.exportPrincipal(clientPrincipal, clientKeytab.toFile());
        } catch (KrbException t) {
            t.printStackTrace();
        }
    }
}
