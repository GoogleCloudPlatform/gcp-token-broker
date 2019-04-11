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

package com.google.cloud.broker.authentication;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.login.Configuration;

import com.google.cloud.broker.settings.AppSettings;
import io.grpc.Status;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;


public class SpnegoAuthenticator  {

    private Subject subject;

    public SpnegoAuthenticator() {
        try {
            LoginContext loginContext = new LoginContext(
                "", new Subject(), null, getConfiguration());
            loginContext.login();
            subject = loginContext.getSubject();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
    }

    private static Configuration getConfiguration() {
        AppSettings settings = AppSettings.getInstance();
        String principal =
            settings.getProperty("BROKER_SERVICE_NAME") + "/" +
            settings.getProperty("BROKER_SERVICE_HOSTNAME") + "@" +
            settings.getProperty("BROKER_REALM");
        String keyTab = settings.getProperty("KEYTAB_PATH");
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, String> options = new HashMap<String, String>();
            options.put("principal", principal);
            options.put("keyTab", keyTab);
            options.put("doNotPrompt", "true");
            options.put("useKeyTab", "true");
            options.put("storeKey", "true");
            options.put("isInitiator", "false");
            return new AppConfigurationEntry[] {
                new AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
            };
            }
        };
    }

    public String authenticateUser() {
        String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();
        if (! authorizationHeader.startsWith("Negotiate ")) {
            throw Status.UNAUTHENTICATED.withDescription("Use \"authorization: Negotiate <token>\" metadata to authenticate").asRuntimeException();
        }
        String spnegoToken = authorizationHeader.split("\\s")[1];

        String authenticatedUser = Subject.doAs(subject, new PrivilegedAction<String>() {
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
                    throw new RuntimeException(e);
                }
            }
        });

        return authenticatedUser;
    }

}