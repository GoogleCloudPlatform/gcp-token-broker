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

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretManager {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** Download a specific secret. */
  private static void downloadSecret(String secretUri, String fileName, boolean required) {
    SecretManagerServiceClient client;
    try {
      client = SecretManagerServiceClient.create();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // Fetch the secret from Secret Manager
    SecretVersionName secretVersionName = SecretVersionName.parse(secretUri);
    AccessSecretVersionRequest request =
        AccessSecretVersionRequest.newBuilder().setName(secretVersionName.toString()).build();
    AccessSecretVersionResponse response = null;
    try {
      response = client.accessSecretVersion(request);
    } catch (NotFoundException e) {
      if (required) {
        throw new RuntimeException(e);
      } else {
        logger.warn(
            "Could not download secret `"
                + secretUri
                + "`"
                + " to `"
                + fileName
                + "`. Error was: "
                + e.getMessage());
      }
    }
    if (response != null) {
      byte[] secretValue = response.getPayload().getData().toByteArray();
      // Save the secret value to disk
      Path secretPath = Path.of(fileName);
      secretPath.getParent().toFile().mkdirs();
      try {
        Files.write(secretPath, secretValue);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      logger.info("Downloaded secret `" + secretUri + "`" + " to `" + fileName + "`");
    }
  }

  /** Download all secrets specified in the settings. */
  public static void downloadSecrets() {
    List<? extends Config> downloads =
        AppSettings.getInstance().getConfigList(AppSettings.SECRET_MANAGER_DOWNLOADS);
    if (downloads.size() > 0) {
      // Download all secrets specified in the settings
      for (Config download : downloads) {
        boolean required;
        try {
          required = download.getBoolean("required");
        } catch (ConfigException.Missing e) {
          required = true;
        }
        downloadSecret(download.getString("secret"), download.getString("file"), required);
      }
    }
  }
}
