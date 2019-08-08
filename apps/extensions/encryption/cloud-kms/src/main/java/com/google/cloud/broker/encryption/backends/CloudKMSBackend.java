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

package com.google.cloud.broker.encryption.backends;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;

import java.io.IOException;

public class CloudKMSBackend extends AbstractEncryptionBackend {


    public byte[] decrypt(String cryptoKey, byte[] cipherText) {
        AppSettings settings = AppSettings.getInstance();
        String projectId = settings.getProperty("GCP_PROJECT");
        String region = settings.getProperty("GCP_REGION");
        String keyRing = settings.getProperty("ENCRYPTION_CRYPTO_KEY_RING");

        byte[] plainText;
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String resourceName = CryptoKeyName.format(projectId, region, keyRing, cryptoKey);
            DecryptResponse response = client.decrypt(resourceName, ByteString.copyFrom(cipherText));
            plainText = response.getPlaintext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return plainText;
    }


    public byte[] encrypt(String cryptoKey, byte[] plainText) {
        AppSettings settings = AppSettings.getInstance();
        String projectId = settings.getProperty("GCP_PROJECT");
        String region = settings.getProperty("GCP_REGION");
        String keyRing = settings.getProperty("ENCRYPTION_CRYPTO_KEY_RING");

        byte[] cipherText;
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String resourceName = CryptoKeyName.format(projectId, region, keyRing, cryptoKey);
            EncryptResponse response = client.encrypt(resourceName, ByteString.copyFrom(plainText));
            cipherText = response.getCiphertext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return cipherText;
    }

}
