/*
 * Copyright 2020 Google LLC
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

package com.google.cloud.broker.secretmanager;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.secretmanager.v1beta1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1beta1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1beta1.SecretVersionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretManager {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final static Path SECRETS_FOLDER = Path.of(AppSettings.getInstance().getString(AppSettings.SECRETS_FOLDER));

    /**
     * Download a specific secret as a file in the secrets folder.
     */
    private static void downloadSecret(String secretId) throws IOException {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName name = SecretVersionName.of(projectId, secretId, "latest");
            AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder().setName(name.toString()).build();
            AccessSecretVersionResponse response = client.accessSecretVersion(request);
            byte[] secretValue = response.getPayload().getData().toByteArray();
            Path secretFile = SECRETS_FOLDER.resolve(secretId);
            Files.write(secretFile, secretValue);
            logger.info("Downloaded secret: " + secretId);
        }
    }

    /**
     * Download all secrets specified in the settings as files in the secrets folder.
     */
    public static void downloadSecrets() {
        // Create the secrets folder if it doesn't already exists
        if (!Files.exists(SECRETS_FOLDER)) {
            try {
                logger.info("Creating secrets folder: " + SECRETS_FOLDER);
                Files.createDirectory(SECRETS_FOLDER);
                logger.info("Created secrets folder: " + SECRETS_FOLDER);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Download all the secrets specified in the settings
        List<String> downloads = AppSettings.getInstance().getStringList(AppSettings.SECRETS_DOWNLOAD);
        logger.info("Downloading secrets: " + String.join(",", downloads));
        for (String secretId : downloads) {
            secretId = secretId.trim();
            try {
                downloadSecret(secretId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}