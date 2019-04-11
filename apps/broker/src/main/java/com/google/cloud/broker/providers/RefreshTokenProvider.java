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

package com.google.cloud.broker.providers;

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
import com.google.api.client.util.Clock;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.authorization.RefreshToken;
import com.google.cloud.broker.encryption.EncryptionUtils;
import com.google.cloud.datastore.Blob;
import io.grpc.Status;


public class RefreshTokenProvider extends AbstractProvider {

    private static String AUTHZ_ERROR_MESSAGE = "GCP Token Broker authorization is invalid or has expired for user: %s";

    public String getGoogleIdentify(String owner) {
        AppSettings settings = AppSettings.getInstance();
        String username = owner.split("@")[0];
        return String.format("%s@%s", username, settings.getProperty("DOMAIN_NAME"));
    }

    @Override
    public AccessToken getAccessToken(String owner, String scope) {
        String googleIdentity = getGoogleIdentify(owner);

        // Fetch refresh token from the database
        RefreshToken refreshToken = null;
        try {
            refreshToken = (RefreshToken) Model.get(RefreshToken.class, googleIdentity);
        }
        catch (DatabaseObjectNotFound e) {
            throw Status.PERMISSION_DENIED.withDescription(String.format(AUTHZ_ERROR_MESSAGE, owner)).asRuntimeException();
        }

        // Decrypt the refresh token's value
        AppSettings settings = AppSettings.getInstance();
        String cryptoKey = settings.getProperty("ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY");
        byte[] encryptedValue = ((Blob) refreshToken.getValue("value")).toByteArray();
        String decryptedValue = new String(EncryptionUtils.decrypt(cryptoKey, encryptedValue));

        // Load OAuth client secret
        File secretJson = new java.io.File(settings.getProperty("CLIENT_SECRET_PATH"));
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
                Clock.SYSTEM.currentTimeMillis() + response.getExpiresInSeconds() * 1000);
    }

}
