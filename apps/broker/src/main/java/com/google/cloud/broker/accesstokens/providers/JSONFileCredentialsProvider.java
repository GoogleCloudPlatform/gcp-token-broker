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

package com.google.cloud.broker.accesstokens.providers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import com.google.auth.oauth2.GoogleCredentials;

import com.google.cloud.broker.accesstokens.AccessToken;
import com.google.cloud.broker.settings.AppSettings;


/**
 * Uses the credentials in a JSON file to generate access tokens.
 * The JSON file can contain a Service Account key file in JSON format from
 * the Google Developers Console or a stored user credential using the format
 * supported by the Cloud SDK.
 * This is NOT recommended for production.
 */
public class JSONFileCredentialsProvider extends AbstractProvider {

    private AppSettings settings = AppSettings.getInstance();

    @Override
    public AccessToken getAccessToken(String owner, String scope) {
        try {
            String basedir = settings.getProperty("JSON_FILE_CREDENTIALS_PROVIDER_BASE_DIR", "");
            Path path = Paths.get(basedir, getGoogleIdentity(owner) + ".json");
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(Files.readAllBytes(path)));
            com.google.auth.oauth2.AccessToken token = credentials
                    .createScoped(scope)
                    .refreshAccessToken();
            return new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getGoogleIdentity(String owner) {
        AppSettings settings = AppSettings.getInstance();
        String username = owner.split("@")[0];
        return username;
    }


}
