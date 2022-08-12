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

package com.google.cloud.broker.client.credentials;

import com.google.auth.oauth2.AccessToken;

import com.google.cloud.broker.client.endpoints.GetAccessToken;
import com.google.cloud.broker.client.connect.BrokerServerInfo;

public class BrokerSessionCredentials extends BrokerBaseCredentials {

    private String sessionToken;

    public BrokerSessionCredentials(BrokerServerInfo serverInfo, String sessionToken) {
        super(serverInfo);
        this.sessionToken = sessionToken;
    }

    public AccessToken refreshAccessToken() {
        return GetAccessToken.submitDelegatedAuth(serverInfo, sessionToken);
    }

}
