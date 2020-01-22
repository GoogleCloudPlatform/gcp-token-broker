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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers.experimental;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Collection;

import com.google.auth.oauth2.GoogleCredentials;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.AbstractProvider;
import com.google.cloud.broker.settings.AppSettings;
import io.grpc.Status;


/**
 * Uses the credentials in a JSON file to generate access tokens.
 * The JSON file can contain a Service Account key file in JSON format from
 * the Google Developers Console or a stored user credential using the format
 * supported by the Cloud SDK.
 * This is an experimental backend that might be removed or modified in a future release.
 * This is NOT recommended for production.
 */
public class JSONFileCredentialsProvider extends AbstractProvider {

    private static String AUTHZ_ERROR_MESSAGE = "GCP Token Broker authorization is invalid or has expired for identity: %s";

    @Override
    public AccessToken getAccessToken(String googleIdentity, Collection<String> scopes) {
        try {
            String basedir = AppSettings.getInstance().getString(AppSettings.JSON_FILE_CREDENTIALS_PROVIDER_BASE_DIR);
            Path path = Paths.get(basedir, googleIdentity.split("@")[0] + ".json");
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(Files.readAllBytes(path)));
            com.google.auth.oauth2.AccessToken token = credentials
                    .createScoped(scopes)
                    .refreshAccessToken();
            return new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
        } catch (NoSuchFileException e) {
            throw Status.PERMISSION_DENIED.withDescription(String.format(AUTHZ_ERROR_MESSAGE, googleIdentity)).asRuntimeException();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
