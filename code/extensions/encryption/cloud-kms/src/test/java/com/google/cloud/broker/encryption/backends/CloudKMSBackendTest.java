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

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class CloudKMSBackendTest {

    private static SettingsOverride backupSettings;

    @BeforeClass
    public static void setUpClass(){
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.ENCRYPTION_KEK_URI, "projects/" + projectId + "/locations/global/keyRings/testkeyring/cryptoKeys/testkey",
            AppSettings.ENCRYPTION_DEK_URI, "gs://" + projectId + "-testbucket/testkey.json"
        ));
    }

    @AfterClass
    public static void teardDownClass() throws Exception {
        // Restore settings
        backupSettings.restore();
    }

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

    /**
     * Encryption backend shall handle at least 1000 QPS
     */
    @Test
    public void testQPS() {
        CloudKMSBackend aead = new CloudKMSBackend();
        java.util.Random r = new java.util.Random();
        byte[] plainText = new byte[512];
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            r.nextBytes(plainText);
            byte[] cipherText = aead.encrypt(plainText);
            byte[] decrypted = aead.decrypt(cipherText);
            assertArrayEquals(decrypted, plainText);
        }
        long t1 = System.currentTimeMillis();
        long dt = t1 - t0;
        assertTrue(dt < 1000);
    }
}
