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

import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.delegation.web.DelegationTokenIdentifier;

import com.google.cloud.hadoop.util.AccessTokenProvider;
import com.google.cloud.hadoop.fs.gcs.auth.AbstractDelegationTokenBinding;


public class BrokerDelegationTokenBinding extends AbstractDelegationTokenBinding {

    public BrokerDelegationTokenBinding() {
        super(BrokerTokenIdentifier.KIND);
    }

    @Override
    public AccessTokenProvider deployUnbonded() throws IOException {
        return new BrokerAccessTokenProvider(getService());
    }

    @Override
    public AccessTokenProvider bindToTokenIdentifier(DelegationTokenIdentifier retrievedIdentifier) throws IOException {
        return new BrokerAccessTokenProvider(getService(), (BrokerTokenIdentifier) retrievedIdentifier);
    }

    @Override
    public DelegationTokenIdentifier createTokenIdentifier() throws IOException {
        return createEmptyIdentifier();
    }

    @Override
    public DelegationTokenIdentifier createTokenIdentifier(Text renewer) throws IOException {
        UserGroupInformation ugi = UserGroupInformation.getCurrentUser();
        String user = ugi.getUserName();
        Text owner = new Text(user);
        Text realUser = null;
        if (ugi.getRealUser() != null) {
            realUser = new Text(ugi.getRealUser().getUserName());
        }
        return new BrokerTokenIdentifier(
            getFileSystem().getConf(),
            owner,
            renewer,
            realUser,
            getService());
    }

    @Override
    public DelegationTokenIdentifier createEmptyIdentifier() {
        return new BrokerTokenIdentifier();
    }
}
