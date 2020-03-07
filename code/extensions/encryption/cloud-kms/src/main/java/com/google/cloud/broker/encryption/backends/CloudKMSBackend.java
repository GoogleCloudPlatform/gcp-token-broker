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

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1.model.DecryptResponse;
import com.google.api.services.cloudkms.v1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1.model.EncryptResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.broker.encryption.backends.keyset.KeysetManager;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.proto.KeyTemplate;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.Constants;
import com.google.cloud.broker.encryption.backends.keyset.KeysetUtils;

/**
 * EnvelopeEncryptionBackend uses a static key stored in Cloud Storage or the local filesystem.
 * Cloud KMS is called once at startup to decrypt the static key.
 * This is the preferred encryption backend when high-throughput is a requirement.
 */
public class CloudKMSBackend extends AbstractEncryptionBackend {

    private static final String MEMORY = "memory";
    private static final String KMS_API = "https://www.googleapis.com/auth/cloudkms";

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to register Tink Aead",e);
        }
    }
    private Aead aead;
    private static KeyTemplate KEY_TEMPLATE = AeadKeyTemplates.AES256_GCM;


    public CloudKMSBackend(){
        String kekUri = AppSettings.getInstance().getString(AppSettings.ENCRYPTION_KEK_URI);
        String dekUri = AppSettings.getInstance().getString(AppSettings.ENCRYPTION_DEK_URI);

        if (kekUri.equalsIgnoreCase(MEMORY)) {
            // Experimental feature: Store the KEK in memory instead of Cloud KMS.
            try {
                aead = KeysetHandle.generateNew(KEY_TEMPLATE).getPrimitive(Aead.class);
                return;
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            CloudKMS kmsClient = getKMSClient();
            aead = readKeyset(dekUri, kekUri, kmsClient)
                .getPrimitive(Aead.class);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize encryption backend", e);
        }

    }

    @Override
    public byte[] decrypt(byte[] cipherText) {
        try {
            return aead.decrypt(cipherText, null);
        } catch (GeneralSecurityException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] encrypt(byte[] plainText) {
        try {
            return aead.encrypt(plainText, null);
        } catch (GeneralSecurityException e){
            throw new RuntimeException(e);
        }
    }

    private static CloudKMS getKMSClient() {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault().createScoped(KMS_API);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new CloudKMS.Builder(
            Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), new HttpCredentialsAdapter(credentials)
        ).setApplicationName(Constants.APPLICATION_NAME).build();
    }

    private static KeysetHandle readKeyset(String dekUri, String kekUri, CloudKMS kmsClient) {
        try {
            Aead kek = new GcpKmsAead(kmsClient, kekUri);
            KeysetManager keysetReader = KeysetUtils.getKeysetManager(dekUri);
            return KeysetHandle.read(keysetReader, kek);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read Keyset `" + dekUri + "` with KMS key `" + kekUri + "`", e);
        }
    }

    public static KeysetHandle generateAndWriteKeyset(String dekUri, String keyUri) {
        return generateAndWriteKeyset(KEY_TEMPLATE, dekUri, keyUri, getKMSClient());
    }

    private static KeysetHandle generateAndWriteKeyset(KeyTemplate keyTemplate, String dekUri, String kekUri, CloudKMS kmsClient) {
        try {
            Aead kek = new GcpKmsAead(kmsClient, kekUri);
            KeysetHandle keysetHandle = KeysetHandle.generateNew(keyTemplate);
            KeysetWriter keysetWriter = KeysetUtils.getKeysetManager(dekUri);
            keysetHandle.write(keysetWriter, kek);
            return keysetHandle;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to write Keyset `" + dekUri + "` with KMS key `" + kekUri + "`", e);
        }
    }


    public static final class GcpKmsAead implements Aead {

        private final CloudKMS kmsClient;

        // The location of a key encryption key (KEK) in Google Cloud KMS.
        // Valid values have this format: projects/*/locations/*/keyRings/*/cryptoKeys/*.
        // See https://cloud.google.com/kms/docs/object-hierarchy.
        private final String kekUri;

        GcpKmsAead(CloudKMS kmsClient, String kekUri) throws GeneralSecurityException {
            this.kmsClient = kmsClient;
            this.kekUri = kekUri;
        }

        @Override
        public byte[] encrypt(final byte[] plaintext, final byte[] aad) throws GeneralSecurityException {
            try {
                EncryptRequest request =
                    new EncryptRequest().encodePlaintext(plaintext).encodeAdditionalAuthenticatedData(aad);
                EncryptResponse response =
                    this.kmsClient
                        .projects()
                        .locations()
                        .keyRings()
                        .cryptoKeys()
                        .encrypt(this.kekUri, request)
                        .execute();
                return response.decodeCiphertext();
            } catch (IOException e) {
                throw new GeneralSecurityException("encryption failed", e);
            }
        }

        @Override
        public byte[] decrypt(final byte[] ciphertext, final byte[] aad) throws GeneralSecurityException {
            try {
                DecryptRequest request =
                    new DecryptRequest().encodeCiphertext(ciphertext).encodeAdditionalAuthenticatedData(aad);
                DecryptResponse response =
                    this.kmsClient
                        .projects()
                        .locations()
                        .keyRings()
                        .cryptoKeys()
                        .decrypt(this.kekUri, request)
                        .execute();
                return response.decodePlaintext();
            } catch (IOException e) {
                throw new GeneralSecurityException("decryption failed", e);
            }
        }
    }

}