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

import java.io.FileInputStream;
import java.io.IOException;

import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

public class GoogleCredentialsFactory {

    public static GoogleCredentialsDetails createCredentialsDetails(boolean generateAccessToken, String... scopes) {
        String jsonPath = System.getenv().get("GOOGLE_APPLICATION_CREDENTIALS");
        GoogleCredentials credentials;
        String email;
        String accessToken = null;
        if (jsonPath != null) {
            // Use the JSON private key if provided
            try {
                credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(jsonPath))
                    .createScoped(scopes);
                email = ((ServiceAccountCredentials) credentials).getAccount();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            // Fall back to using the default Compute Engine service account
            credentials = ComputeEngineCredentials.create();
            email = ((ComputeEngineCredentials) credentials).getAccount();
        }
        if (generateAccessToken) {
            try {
                accessToken = credentials.refreshAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new GoogleCredentialsDetails(credentials, email, accessToken);
    }

}
