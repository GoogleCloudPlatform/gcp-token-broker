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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.io.IOException;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import io.grpc.Status;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;


public class ServiceAccountProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String googleIdentity, List<String> scopes) {
        if (! googleIdentity.endsWith(".iam.gserviceaccount.com")) {
            throw new IllegalArgumentException("Google identity `" + googleIdentity + "` is not a service account");
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            ImpersonatedCredentials impersonatedCredentials = ImpersonatedCredentials.create(credentials, googleIdentity, null, scopes, 3600);
            com.google.auth.oauth2.AccessToken accessToken = impersonatedCredentials.refreshAccessToken();
            return new AccessToken(accessToken.getTokenValue(), accessToken.getExpirationTime().getTime());
        } catch (IOException e) {
            throw Status.PERMISSION_DENIED.asRuntimeException();
        }
    }

}
