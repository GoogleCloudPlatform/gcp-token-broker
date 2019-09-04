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

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import io.grpc.Status;

import com.google.cloud.broker.accesstokens.AccessToken;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.utils.TimeUtils;


public class RefreshTokenProvider extends AbstractProvider {

    private static String AUTHZ_ERROR_MESSAGE = "GCP Token Broker authorization is invalid or has expired for user: %s";

    public String getGoogleIdentity(String owner) {
        String username;
        try {
            username = owner.split("@")[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException();
        }
        if (username.length() == 0) {
            throw new IllegalArgumentException();
        }
        String domain = AppSettings.requireProperty("DOMAIN_NAME");
        String googleIdentity = String.format("%s@%s", username, domain);
        return googleIdentity;
    }

    @Override
    public AccessToken getAccessToken(String owner, String scope) {
        // Map the Google identity
        String googleIdentity = getGoogleIdentity(owner);

        // Fetch refresh token from the database
        RefreshToken refreshToken = null;
        try {
            refreshToken = (RefreshToken) Model.get(RefreshToken.class, googleIdentity);
        }
        catch (DatabaseObjectNotFound e) {
            throw Status.PERMISSION_DENIED.withDescription(String.format(AUTHZ_ERROR_MESSAGE, owner)).asRuntimeException();
        }

        // Decrypt the refresh token's value
        byte[] encryptedValue = (byte[]) refreshToken.getValue("value");
        String decryptedValue = new String(AbstractEncryptionBackend.getInstance().decrypt(encryptedValue));

        // Load OAuth client secret
        File secretJson = new java.io.File(AppSettings.requireProperty("CLIENT_SECRET_PATH"));
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        GoogleClientSecrets clientSecrets;
        try {
            InputStream in = new FileInputStream(secretJson);
            clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Generate a new access token
        TokenResponse response = null;
        try {
            response = new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                new JacksonFactory(),
                decryptedValue,
                clientSecrets.getDetails().getClientId(),
                clientSecrets.getDetails().getClientSecret()
            ).execute();
        } catch (IOException e) {
            throw Status.PERMISSION_DENIED.withDescription(String.format(AUTHZ_ERROR_MESSAGE, owner)).asRuntimeException();
        }

        return new AccessToken(
            response.getAccessToken(),
                TimeUtils.currentTimeMillis() + response.getExpiresInSeconds() * 1000);
    }

}
