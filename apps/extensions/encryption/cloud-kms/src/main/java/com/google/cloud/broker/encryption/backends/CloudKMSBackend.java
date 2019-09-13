/*
 * Copyright 2019 Google LLC
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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1.model.DecryptResponse;
import com.google.api.services.cloudkms.v1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1.model.EncryptResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.EnvUtils;
import com.google.cloud.broker.utils.GoogleCredentialsDetails;
import com.google.cloud.broker.utils.GoogleCredentialsFactory;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetReader;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.proto.Keyset;
import org.conscrypt.Conscrypt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.security.GeneralSecurityException;
import java.security.Security;


/**
 * EnvelopeEncryptionBackend uses a static key stored in Cloud Storage.
 * Cloud KMS is called once at startup to decrypt the static key.
 * This is the preferred encryption backend when high-throughput is a requirement.
 */
public class CloudKMSBackend extends AbstractEncryptionBackend {

    public static final String ENCRYPTION_KEK_URI = "ENCRYPTION_KEK_URI";
    public static final String ENCRYPTION_DEK_URI = "ENCRYPTION_DEK_URI";
    public static final String MEMORY = "memory";

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to register Tink Aead",e);
        }
        try {
            if (!Security.getProviders()[0].getName().equals("Conscrypt")) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            }
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Unable to configure Conscrypt JCE Provider", e);
        }
    }
    private Aead aead;
    public static KeyTemplate KEY_TEMPLATE = AeadKeyTemplates.AES256_GCM;
    private String kekUri;
    private String dekUri;

    public CloudKMSBackend(){
        kekUri = AppSettings.requireProperty(ENCRYPTION_KEK_URI);
        dekUri = AppSettings.requireProperty(ENCRYPTION_DEK_URI);

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
            URI uri = new URI(dekUri);
            Storage storageClient = getStorageClient();
            CloudKMS kmsClient = getKMSClient();
            aead = readKeyset(uri.getAuthority(),
                uri.getPath().substring(1), kekUri, storageClient, kmsClient)
                .getPrimitive(Aead.class);
        } catch (URISyntaxException | GeneralSecurityException e) {
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

    public static Storage getStorageClient() {
        GoogleCredentialsDetails details = GoogleCredentialsFactory
            .createCredentialsDetails(false, "https://www.googleapis.com/auth/devstorage.read_write");
        return StorageOptions.newBuilder()
            .setCredentials(details.getCredentials())
            .build()
            .getService();
    }

    public static CloudKMS getKMSClient() {
        GoogleCredentialsDetails details = GoogleCredentialsFactory
            .createCredentialsDetails(false, "https://www.googleapis.com/auth/cloudkms");
        return new CloudKMS.Builder(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), new HttpCredentialsAdapter(details.getCredentials())).build();
    }

    public static KeysetHandle readKeyset(String bucket, String name, String kekUri, Storage storage, CloudKMS kmsClient) {
        try {
            Aead kek = new GcpKmsAead(kmsClient, kekUri);
            KeysetReader keysetReader = new CloudStorageKeysetReader(bucket, name, storage);
            return KeysetHandle.read(keysetReader, kek);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read Keyset from gs://" + bucket + "/" + name + " with KMS key " + kekUri, e);
        }
    }

    public static KeysetHandle generateAndWrite(String bucket, String dekName, String keyUri) {
        return generateAndWrite(KEY_TEMPLATE, bucket, dekName, keyUri, getStorageClient(), getKMSClient());
    }

    public static KeysetHandle generateAndWrite(KeyTemplate keyTemplate, String bucket, String dekName, String keyUri, Storage storage, CloudKMS kms) {
        try {
            Aead kek = new GcpKmsAead(kms, keyUri);
            KeysetWriter keysetWriter = new CloudStorageKeysetWriter(bucket, dekName, storage);
            KeysetHandle keysetHandle = KeysetHandle.generateNew(keyTemplate);
            keysetHandle.write(keysetWriter, kek);
            return keysetHandle;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read Keyset from gs://" + bucket + "/" + dekName + " with KMS key " + keyUri, e);
        }
    }

    public static class CloudStorageKeysetReader implements KeysetReader {
        private String bucket;
        private String name;
        private Storage storage;

        public CloudStorageKeysetReader(String bucket, String name, Storage storage) {
            this.bucket = bucket;
            this.name = name;
            this.storage = storage;
        }

        @Override
        public Keyset read() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public EncryptedKeyset readEncrypted() throws IOException {
            return JsonKeysetReader
                .withBytes(storage.readAllBytes(bucket, name))
                .readEncrypted();
        }
    }

    public static class CloudStorageKeysetWriter implements KeysetWriter {
        private String bucket;
        private String name;
        private Storage storage;

        public CloudStorageKeysetWriter(String bucket, String name, Storage storage) {
            this.bucket = bucket;
            this.name = name;
            this.storage = storage;
        }

        @Override
        public void write(Keyset keyset) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(EncryptedKeyset keyset) throws IOException {
            WriteChannel wc = storage.writer(BlobInfo.newBuilder(BlobId.of(bucket, name)).build());
            OutputStream os = Channels.newOutputStream(wc);
            JsonKeysetWriter
                .withOutputStream(os)
                .write(keyset);
            os.close();
            wc.close();
        }
    }

    public static final class GcpKmsAead implements Aead {

        private final CloudKMS kmsClient;

        // The location of a key encryption key (KEK) in Google Cloud KMS.
        // Valid values have this format: projects/*/locations/*/keyRings/*/cryptoKeys/*.
        // See https://cloud.google.com/kms/docs/object-hierarchy.
        private final String kekUri;

        public GcpKmsAead(CloudKMS kmsClient, String kekUri) throws GeneralSecurityException {
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
