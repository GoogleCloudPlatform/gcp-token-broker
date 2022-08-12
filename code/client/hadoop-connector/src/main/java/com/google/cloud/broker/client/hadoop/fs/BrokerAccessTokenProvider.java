// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.client.hadoop.fs;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Collections;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import com.google.cloud.hadoop.util.AccessTokenProvider;

import com.google.cloud.broker.client.credentials.BrokerBaseCredentials;
import com.google.cloud.broker.client.credentials.BrokerKerberosCredentials;
import com.google.cloud.broker.client.credentials.BrokerSessionCredentials;
import com.google.cloud.broker.client.connect.BrokerServerInfo;

public final class BrokerAccessTokenProvider implements AccessTokenProvider {

    private Configuration config;
    private AccessToken accessToken;
    private BrokerTokenIdentifier tokenIdentifier;
    private Text service;
    private final static AccessToken EXPIRED_TOKEN = new AccessToken("", -1L);

    public BrokerAccessTokenProvider(Text service) {
        this(service, null);
    }

    public BrokerAccessTokenProvider(Text service, BrokerTokenIdentifier bti) {
        this.service = service;
        this.tokenIdentifier = bti;
        this.accessToken = EXPIRED_TOKEN;
    }

    @Override
    public AccessToken getAccessToken() {
        return this.accessToken;
    }

    @Override
    public void refresh() {
        // Retrieve the current and login users from the ambient context
        UserGroupInformation currentUser;
        UserGroupInformation loginUser;
        try {
            currentUser = UserGroupInformation.getCurrentUser();
            loginUser = UserGroupInformation.getLoginUser();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Instantiate the proper credentials class based on the type of authentication (direct or delegated)
        BrokerServerInfo serverInfo = Utils.getBrokerDetailsFromConfig(config);
        BrokerBaseCredentials credentials;
        if (tokenIdentifier == null) {
            // Use direct authentication
            credentials = new BrokerKerberosCredentials(
                serverInfo,
                currentUser.getUserName(),
                Collections.singleton(BrokerTokenIdentifier.GCS_SCOPE),
                Utils.getTarget(config, service));
        }
        else {
            // Use delegated authentication
            credentials = new BrokerSessionCredentials(serverInfo, tokenIdentifier.getSessionToken());
        }

        // Generate the access token
        loginUser.doAs((PrivilegedAction<Void>) () -> {
            try {
                credentials.refresh();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        com.google.auth.oauth2.AccessToken token = credentials.getAccessToken();
        accessToken = new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
    }

    @Override
    public void setConf(Configuration config) {
        this.config = config;
    }

    @Override
    public Configuration getConf() {
        return this.config;
    }

}