/*
 * Copyright 2020 Google LLC
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
package com.google.cloud.broker.authentication.backends;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import com.sun.security.auth.module.Krb5LoginModule;
import org.apache.commons.io.FileUtils;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.apache.kerby.util.NetworkUtil;

/**
 * Wrapper around Kerby's KDC server. Used for testing.
 */
public class FakeKDC {

    private String realm;
    private SimpleKdcServer kdcServer;
    private Path rootDir;
    private Path brokerKeytabDir;
    private Path userKeytabDir;
    private List<String> principals = new ArrayList<>();

    public FakeKDC(String realm) {
        this.realm = realm;
    }

    /**
     * Log in the given principal and return the subject.
     */
    public Subject login(String principal) {
        Path keytab = getKeytabPath(principal);

        // Set login options
        final Map<String, String> options = new HashMap<>();
        options.put("keyTab", keytab.toString());
        options.put("principal", principal);
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");
        options.put("refreshKrb5Config", "true");
        options.put("storeKey", "true");
        options.put("useKeyTab", "true");

        // Execute the login
        Subject subject = new Subject();
        Krb5LoginModule krb5LoginModule = new Krb5LoginModule();
        krb5LoginModule.initialize(subject, null, new HashMap<String, String>(), options);
        try {
            krb5LoginModule.login();
            krb5LoginModule.commit();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
        return subject;
    }

    /**
     * Returns path of the given principal's keytab on the filesystem.
     */
    public Path getKeytabPath(String principal) {
        String name = principal.split("@")[0].split("/")[0];
        if (name.equals("broker")) {
            return brokerKeytabDir.resolve("broker.keytab");
        }
        else {
            return userKeytabDir.resolve(name + ".keytab");
        }
    }

    /**
     * Create given principal in the KDC and generate a keytab.
     */
    public void createPrincipal(String principal) {
        try {
            kdcServer.createPrincipal(principal);
            File keytabFile = getKeytabPath(principal).toFile();
            kdcServer.exportPrincipal(principal, keytabFile);
            principals.add(principal);
        } catch (KrbException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start the server and create some temporary directories to store keytabs.
     */
    public void start() {
        try {
            rootDir = Files.createTempDirectory("root");
            brokerKeytabDir = Files.createDirectory(rootDir.resolve("broker-keytabs"));
            userKeytabDir = Files.createDirectory(rootDir.resolve("user-keytabs"));

            // Initialize the KDC server
            kdcServer = new SimpleKdcServer();
            kdcServer.setWorkDir(rootDir.toFile());
            kdcServer.setKdcRealm(realm);
            kdcServer.setKdcHost("localhost");
            kdcServer.setAllowTcp(false);
            kdcServer.setAllowUdp(true);
            kdcServer.setKdcUdpPort(NetworkUtil.getServerPort());

            // Start the KDC server
            kdcServer.init();
            kdcServer.start();
        } catch (KrbException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            kdcServer.getKadmin().deleteBuiltinPrincipals();
            for (String principal : principals) {
                kdcServer.deletePrincipal(principal);
            }
            kdcServer.stop();
            FileUtils.deleteDirectory(rootDir.toFile());
        } catch (KrbException | IOException e) {
            throw new RuntimeException(e);
        }
    }

}