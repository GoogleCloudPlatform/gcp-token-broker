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

import org.ietf.jgss.*;

public final class SpnegoUtils {

    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";
    private static final String KRB5_MECHANISM_OID = "1.2.840.113554.1.2.2";
    private static final String KRB5_PRINCIPAL_NAME_OID = "1.2.840.113554.1.2.2.1";

    public static byte[] newSPNEGOToken(String brokerServiceName, String brokerHostname, String brokerRealm) throws GSSException {
        // Create GSS context for the broker service and the logged-in user
        Oid krb5Mechanism = new Oid(KRB5_MECHANISM_OID);
        Oid krb5PrincipalNameType = new Oid(KRB5_PRINCIPAL_NAME_OID);
        Oid spnegoOid = new Oid(SPNEGO_OID);
        GSSManager manager = GSSManager.getInstance();
        String servicePrincipal = brokerServiceName + "/" + brokerHostname + "@" + brokerRealm;
        GSSName gssServerName = manager.createName(servicePrincipal , krb5PrincipalNameType, krb5Mechanism);
        GSSContext gssContext = manager.createContext(
            gssServerName, spnegoOid, null, GSSCredential.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(true);
        gssContext.requestCredDeleg(true);

        // Generate the SPNEGO token
        byte[] token = new byte[0];
        token = gssContext.initSecContext(token, 0, token.length);
        return token;
    }

}
