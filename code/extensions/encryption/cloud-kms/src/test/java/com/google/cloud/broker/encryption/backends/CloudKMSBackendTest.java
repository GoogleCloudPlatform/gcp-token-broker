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

package com.google.cloud.broker.encryption.backends;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;

import static org.junit.Assert.*;

public class CloudKMSBackendTest {

    private static String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.ENCRYPTION_KEK_URI, "projects/" + projectId + "/locations/global/keyRings/testkeyring/cryptoKeys/testkey",
        AppSettings.ENCRYPTION_DEK_URI, "gs://" + projectId + "-testbucket/testkey.json"
    ));

    /**
     * Encryption backend shall encrypt and correctly decrypt a given plaintext
     */
    @Test
    public void testEncryptAndDecrypt() {
        CloudKMSBackend aead = new CloudKMSBackend();
        byte[] plainText = "test string".getBytes();
        byte[] cipherText = aead.encrypt(plainText);
        assertFalse(Arrays.equals(plainText, cipherText));
        byte[] decrypted = aead.decrypt(cipherText);
        assertArrayEquals(plainText, decrypted);
    }

}
