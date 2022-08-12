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

import com.google.cloud.broker.client.endpoints.CancelSessionToken;
import com.google.cloud.broker.client.endpoints.RenewSessionToken;
import com.google.cloud.broker.client.connect.BrokerServerInfo;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenRenewer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.conf.Configuration;
import com.google.cloud.hadoop.fs.gcs.auth.GcsDelegationTokens;


public class BrokerTokenRenewer extends TokenRenewer {

    @Override
    public boolean handleKind(Text kind) {
        return BrokerTokenIdentifier.KIND.equals(kind);
    }

    @Override
    public long renew(Token<?> t, Configuration config) throws IOException {
        Token<BrokerTokenIdentifier> token = (Token<BrokerTokenIdentifier>) t;
        BrokerTokenIdentifier tokenIdentifier = (BrokerTokenIdentifier) GcsDelegationTokens.extractIdentifier(token);
        UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
        BrokerServerInfo serverInfo = Utils.getBrokerDetailsFromConfig(config);
        return loginUser.doAs((PrivilegedAction<Long>) () -> {
            return RenewSessionToken.submit(serverInfo, tokenIdentifier.getSessionToken());
        });
    }

    @Override
    public void cancel(Token<?> t, Configuration config) throws IOException {
        Token<BrokerTokenIdentifier> token = (Token<BrokerTokenIdentifier>) t;
        BrokerTokenIdentifier tokenIdentifier = (BrokerTokenIdentifier) GcsDelegationTokens.extractIdentifier(token);
        UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
        BrokerServerInfo serverInfo = Utils.getBrokerDetailsFromConfig(config);
        loginUser.doAs((PrivilegedAction<Void>) () -> {
            CancelSessionToken.submit(serverInfo, tokenIdentifier.getSessionToken());
            return null;
        });
    }

    @Override
    public boolean isManaged(Token<?> token) throws IOException {
        // Return true to indicate that tokens can be renewed and cancelled
        return true;
    }
}