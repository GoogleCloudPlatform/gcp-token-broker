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
import java.util.ArrayList;
import java.util.Set;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.typesafe.config.Config;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;


public class SpnegoAuthenticator extends AbstractAuthenticationBackend {

    private ArrayList<Subject> logins;
    public static final String KEYTABS_PATH = "KEYTABS_PATH";
    public static final String BROKER_SERVICE_NAME = "BROKER_SERVICE_NAME";
    public static final String BROKER_SERVICE_HOSTNAME = "BROKER_SERVICE_HOSTNAME";
    public static final String BROKER_SERVICE_REALM = "BROKER_SERVICE_REALM";
    private static final String NO_VALID_KEYTABS_ERROR = "No valid keytabs found in path `%s` as defined in the `KEYTABS_PATH` setting";

    public SpnegoAuthenticator() {
        Config config = AppSettings.getConfig();
        String keytabsPath = config.getString(KEYTABS_PATH);
        Set<String> serviceNames = ImmutableSet.copyOf(config.getString(BROKER_SERVICE_NAME).split(","));
        Set<String> hostNames = ImmutableSet.copyOf(config.getString(BROKER_SERVICE_HOSTNAME).split(","));
        Set<String> realms = ImmutableSet.copyOf(config.getString(BROKER_SERVICE_REALM).split(","));
        logins = KerberosUtil.loadKeytabs(new File(keytabsPath), serviceNames, hostNames, realms);

        if (logins.size() == 0) {
            throw new IllegalStateException(String.format(NO_VALID_KEYTABS_ERROR, keytabsPath));
        }
    }

    /** Authenticates SPNEGO token and returns username
      * @param authorizationHeader SPNEGO token
      * @return user name
      */
    public String authenticateUser(String authorizationHeader) {
        if (!authorizationHeader.startsWith("Negotiate ")) {
            throw Status.UNAUTHENTICATED.withDescription("Use \"authorization: Negotiate <token>\" metadata to authenticate").asRuntimeException();
        }

        String spnegoToken = authorizationHeader.split("\\s")[1];

        for (Subject login : logins) {
            String authenticatedUser = Subject.doAs(login, new PrivilegedAction<String>() {
                public String run() {
                    try {
                        GSSManager manager = GSSManager.getInstance();
                        Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
                        GSSCredential serverCredential = manager.createCredential(null,
                            GSSCredential.DEFAULT_LIFETIME,
                            spnegoOid,
                            GSSCredential.ACCEPT_ONLY);
                        GSSContext context = manager.createContext(serverCredential);
                        byte[] tokenBytes = BaseEncoding.base64().decode(spnegoToken);
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