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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.io.IOException;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.settings.AppSettings;
import io.grpc.Status;

public class ShadowServiceAccountProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String owner, List<String> scopes, String target) {
        String googleIdentity = getGoogleIdentity(owner);
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            ImpersonatedCredentials impersonatedCredentials = ImpersonatedCredentials.create(credentials, googleIdentity, null, scopes, 3600);
            com.google.auth.oauth2.AccessToken token = impersonatedCredentials.refreshAccessToken();
            AccessToken accessToken = new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
            return getBoundedAccessToken(target, accessToken);
        } catch (IOException e) {
            throw Status.PERMISSION_DENIED.asRuntimeException();
        }
    }

    public String getGoogleIdentity(String owner) {
        String shadowProject = AppSettings.getInstance().getString(AppSettings.SHADOW_PROJECT);
        String shadowPattern = AppSettings.getInstance().getString(AppSettings.SHADOW_USERNAME_PATTERN);
        String username;
        try {
            username = owner.split("@")[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException();
        }
        if (username.length() == 0) {
            throw new IllegalArgumentException();
        }
        return String.format(shadowPattern, username) + "@" + shadowProject + ".iam.gserviceaccount.com";
    }

}
