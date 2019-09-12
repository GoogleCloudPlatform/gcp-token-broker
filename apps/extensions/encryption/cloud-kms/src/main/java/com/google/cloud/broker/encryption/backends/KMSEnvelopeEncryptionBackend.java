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
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1.model.DecryptResponse;
import com.google.api.services.cloudkms.v1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1.model.EncryptResponse;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.broker.settings.AppSettings;
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
public class KMSEnvelopeEncryptionBackend extends AbstractEncryptionBackend {
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
    private String cryptoKey;
    private String dekUri;

    public KMSEnvelopeEncryptionBackend(){
        cryptoKey = AppSettings.requireProperty(AppSettings.ENCRYPTION_CRYPTO_KEY);
        dekUri = AppSettings.requireProperty(AppSettings.ENCRYPTION_DEK_URI);

        if (cryptoKey.equalsIgnoreCase(AppSettings.TEST_ENCRYPTION)) {
            try {
                aead = KeysetHandle.generateNew(KEY_TEMPLATE).getPrimitive(Aead.class);
                return;
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        } else if (cryptoKey.equalsIgnoreCase(AppSettings.NO_ENCRYPTION)) {
            aead = new PlainTextAead();
            return;
        }

        if (!cryptoKey.startsWith("gs://") || dekUri.length() < 10) {
            throw new IllegalArgumentException("Invalid DEK URI '" + dekUri + "' - should be 'gs://bucket/path'");
        }
        if (cryptoKey.length() < "projects/_/locations/_/keyRings/_/cryptoKeys/_".length()) {
            throw new IllegalArgumentException("Invalid master key URI '" + cryptoKey + "' - should be 'projects/_/locations/_/keyRings/_/cryptoKeys/_'");
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

            URI uri = new URI(dekUri);
            Storage storage = getStorageClient(credentials);
            CloudKMS kms = getKMSClient(credentials);

            aead = readKeyset(uri.getAuthority(),
                uri.getPath().substring(1), cryptoKey, storage, kms)
                .getPrimitive(Aead.class);
        } catch (IOException | URISyntaxException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize EnvelopeEncryptionBackend", e);
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

    public static String USER_AGENT = "google-pso-tool/gcp-token-broker/0.4";

    public static Storage getStorageClient(Credentials credentials) {
        return StorageOptions.newBuilder()
            .setCredentials(credentials)
            .setHeaderProvider(FixedHeaderProvider.create("user-agent", USER_AGENT))
            .build()
            .getService();
    }

    public static Storage getStorageClient() {
        try {
            GoogleCredentials creds = GoogleCredentials
                .getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/devstorage.read_write");
            return getStorageClient(creds);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Cloud Storage client application default credentials", e);
        }
    }

    public static CloudKMS getKMSClient() {
        try {
            GoogleCredentials creds = GoogleCredentials
                .getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloudkms");
            return getKMSClient(creds);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Cloud KMS client", e);
        }
    }

    public static CloudKMS getKMSClient(Credentials credentials) {
        return new CloudKMS.Builder(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), new HttpCredentialsAdapter(credentials))
            .setApplicationName(USER_AGENT)
            .build();
    }

    public static KeysetHandle readKeyset(String bucket, String name, String keyUri, Storage storage, CloudKMS kms) {
        try {
            Aead masterKey = new GcpKmsAead(kms, keyUri);
            KeysetReader keysetReader = new CloudStorageKeysetReader(bucket, name, storage);
            return KeysetHandle.read(keysetReader, masterKey);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read Keyset from gs://" + bucket + "/" + name + " with KMS key " + keyUri, e);
        }
    }

    public static KeysetHandle generateAndWrite(String bucket, String name, String keyUri) {
        return generateAndWrite(KEY_TEMPLATE, bucket, name, keyUri, getStorageClient(), getKMSClient());
    }

    public static KeysetHandle generateAndWrite(KeyTemplate keyTemplate, String bucket, String name, String keyUri, Storage storage, CloudKMS kms) {
        try {
            Aead masterKey = new GcpKmsAead(kms, keyUri);
            KeysetWriter keysetWriter = new CloudStorageKeysetWriter(bucket, name, storage);
            KeysetHandle keysetHandle = KeysetHandle.generateNew(keyTemplate);
            keysetHandle.write(keysetWriter, masterKey);
            return keysetHandle;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read Keyset from gs://" + bucket + "/" + name + " with KMS key " + keyUri, e);
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

        // The location of a CryptoKey in Google Cloud KMS.
        // Valid values have this format: projects/*/locations/*/keyRings/*/cryptoKeys/*.
        // See https://cloud.google.com/kms/docs/object-hierarchy.
        private final String kmsKeyUri;

        public GcpKmsAead(CloudKMS kmsClient, String keyUri) throws GeneralSecurityException {
            this.kmsClient = kmsClient;
            this.kmsKeyUri = keyUri;
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
                        .encrypt(this.kmsKeyUri, request)
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
                        .decrypt(this.kmsKeyUri, request)
                        .execute();
                return response.decodePlaintext();
            } catch (IOException e) {
                throw new GeneralSecurityException("decryption failed", e);
            }
        }
    }

    /**
     * Dummy encryption backend that does not encrypt nor decrypt anything.
     * Use only for testing. Do NOT use in production!
     */
    public static class PlainTextAead implements Aead {
        @Override
        public byte[] decrypt(byte[] cipherText, byte[] associatedData) {
            return cipherText;
        }

        @Override
        public byte[] encrypt(byte[] plainText, byte[] associatedData) {
            return plainText;
        }
    }
}
