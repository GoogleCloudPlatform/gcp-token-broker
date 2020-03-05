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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.RandomStringUtils;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;

public class SecretManagerTest {

    /**
     * Returns the name of a directory that doesn't yet exist.
     */
    private Path getAvailableDirectory() {
        while(true) {
            String randomDirectory = "/tmp/" + RandomStringUtils.random(6, true, true);
            if (! Files.exists(Paths.get(randomDirectory))) {
                return Path.of(randomDirectory);
            }
        }
    }

    @Test
    public void testDownload() throws Exception {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        Path secretsDirectory = getAvailableDirectory();
        Object downloads = ConfigFactory.parseString(AppSettings.SECRET_MANAGER_DOWNLOADS + "=[" +
            "{" +
              "secret = \"projects/" + projectId + "/secrets/secretstuff/versions/latest\"," +
              "file = \"" + secretsDirectory.resolve("secretstuff.txt") + "\"" +
            "}" +
        "]").getAnyRef(AppSettings.SECRET_MANAGER_DOWNLOADS);
        try (SettingsOverride override = new SettingsOverride(Map.of(AppSettings.SECRET_MANAGER_DOWNLOADS, downloads))) {
            SecretManager.downloadSecrets();
            String contents = Files.readString(secretsDirectory.resolve("secretstuff.txt"));
            assertEquals(contents, "This is secret stuff");
        }
        // Clean up
        FileUtils.deleteDirectory(secretsDirectory.toFile());
    }

}
