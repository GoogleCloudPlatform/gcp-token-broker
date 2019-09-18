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
import java.util.*;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.login.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;


public class SpnegoAuthenticator extends AbstractAuthenticationBackend {

    private ArrayList<Subject> logins = new ArrayList<Subject>();


    private void initLogin() {
        // Parse the JSON-formatted setting
        String keytabs = AppSettings.getProperty("KEYTABS");
        Iterator<JsonNode> iterator;
        try {
            iterator = new ObjectMapper().readTree(keytabs).elements();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Log in each individual principal
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            JsonNode principal = item.get("principal");
            JsonNode keytab = item.get("keytab");

            if (principal == null || keytab == null) {
                throw new IllegalArgumentException("Invalid `KEYTABS` setting");
            }

            File keytabFile = new File(keytab.asText());
            Subject subject = principalLogin(principal.asText(), keytabFile);
            logins.add(subject);
        }

        if (logins.size() == 0) {
            throw new IllegalArgumentException("Invalid `KEYTABS` setting");
        }
    }


    private Subject principalLogin(String principal, File keytabFile) {
        try {
            LoginContext loginContext = new LoginContext(
                    "", new Subject(), null, getConfiguration(principal, keytabFile));
            loginContext.login();
            Subject subject = loginContext.getSubject();
            return subject;
        } catch (LoginException e) {
            throw new RuntimeException("Failed login for principal `" + principal + "` with keytab `" + keytabFile.getPath() + "`");
        }
    }


    private static Configuration getConfiguration(String principal, File keytabFile) {
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                Map<String, String> options = new HashMap<String, String>();
                options.put("principal", principal);
                options.put("keyTab", keytabFile.getPath());
                options.put("doNotPrompt", "true");
                options.put("useKeyTab", "true");
                options.put("storeKey", "true");
                options.put("isInitiator", "true");
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
                };
            }
        };
    }


    public String authenticateUser(String authorizationHeader) {
        if (logins.isEmpty()) {
            initLogin();
        }

        if (! authorizationHeader.startsWith("Negotiate ")) {
            throw Status.UNAUTHENTICATED.withDescription("Use \"authorization: Negotiate <token>\" metadata to authenticate").asRuntimeException();
        }

        String spnegoToken = authorizationHeader.split("\\s")[1];

        String authenticatedUser = null;

        for (Subject login : logins) {
            authenticatedUser = Subject.doAs(login, new PrivilegedAction<String>() {
                public String run() {
                    try {
                        GSSManager manager = GSSManager.getInstance();
                        Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
                        GSSCredential serverCredential = manager.createCredential(null,
                            GSSCredential.DEFAULT_LIFETIME,
                            spnegoOid,
                            GSSCredential.ACCEPT_ONLY);
                        GSSContext context = manager.createContext((GSSCredential) serverCredential);
                        byte[] tokenBytes = Base64.getDecoder().decode(spnegoToken.getBytes());
                        context.acceptSecContext(tokenBytes, 0, tokenBytes.length);
                        return context.getSrcName().toString();
                    } catch (GSSException e) {
                        return null;
                    }
                }
            });
            if (authenticatedUser != null) {
                return authenticatedUser;
            }
        }

        throw Status.UNAUTHENTICATED.withDescription("SPNEGO authentication failed").asRuntimeException();
    }

}