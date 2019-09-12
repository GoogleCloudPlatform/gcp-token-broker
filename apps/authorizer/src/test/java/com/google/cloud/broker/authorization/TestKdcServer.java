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

import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.client.KrbConfigKey;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.apache.kerby.util.NetworkUtil;


public class TestKdcServer extends SimpleKdcServer {
    public TestKdcServer(String realm, String host) throws KrbException {
        super();
        setKdcRealm(realm);
        setKdcHost(host);
        setAllowTcp(false);
        setAllowUdp(true);
        setKdcUdpPort(NetworkUtil.getServerPort());
        KrbClient krbClnt = getKrbClient();
        KrbConfig krbConfig = krbClnt.getKrbConfig();
        krbConfig.setString(KrbConfigKey.PERMITTED_ENCTYPES,
            "aes128-cts-hmac-sha1-96");
        krbClnt.setTimeout(10 * 1000);
    }
}
