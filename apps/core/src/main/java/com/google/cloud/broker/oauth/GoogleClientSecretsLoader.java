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

package com.google.cloud.broker.oauth;

import java.io.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.cloud.broker.settings.AppSettings;

public class GoogleClientSecretsLoader {

    public static GoogleClientSecrets getSecrets() {
        String jsonPath = AppSettings.getProperty(AppSettings.OAUTH_CLIENT_SECRET_JSON_PATH);
        String clientId = AppSettings.getProperty(AppSettings.OAUTH_CLIENT_ID);
        String clientSecret = AppSettings.getProperty(AppSettings.OAUTH_CLIENT_SECRET);

        if (jsonPath != null) {
            // Load the JSON file if provided
            File secretJson = new java.io.File(jsonPath);
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            try {
                InputStream in = new FileInputStream(secretJson);
                return GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
            } catch (
                IOException e) {
                throw new RuntimeException(e);
            }
        }
        else if (clientId != null && clientSecret != null) {
            // Fall back to using the provided ID and secret
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
            clientSecrets.setWeb(details);
            return clientSecrets;
        }
        else {
            throw new RuntimeException("OAuth misconfigured");
        }
    }

}
