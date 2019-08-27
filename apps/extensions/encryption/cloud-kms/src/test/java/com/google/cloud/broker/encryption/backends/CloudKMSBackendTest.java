package com.google.cloud.broker.encryption.backends;

import java.io.IOException;

import static org.junit.Assert.*;
import com.google.cloud.kms.v1.EncryptResponse;
import org.junit.Before;
import org.junit.Test;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;

import com.google.cloud.broker.settings.AppSettings;


public class CloudKMSBackendTest {

    private final String PROJECT = AppSettings.requireProperty("GCP_PROJECT");
    private final String KEY_RING_REGION = "global";
    private final String KEY_RING = "mykeyring";
    private final String KEY = "mykey";

    @Before
    public void setup() {
        AppSettings.reset();
        AppSettings.setProperty("ENCRYPTION_CRYPTO_KEY_RING_REGION", KEY_RING_REGION);
        AppSettings.setProperty("ENCRYPTION_CRYPTO_KEY_RING", KEY_RING);
    }

    @Test
    public void testEncrypt() {
        // Use the backend to encrypt
        CloudKMSBackend backend = new CloudKMSBackend();
        byte[] encrypted = backend.encrypt(KEY, "abcd".getBytes());

        // Decrypt directly with Cloud KMS
        byte[] decrypted;
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String resourceName = CryptoKeyName.format(PROJECT, KEY_RING_REGION, KEY_RING, KEY);
            DecryptResponse response = client.decrypt(resourceName, ByteString.copyFrom(encrypted));
            decrypted = response.getPlaintext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertArrayEquals("abcd".getBytes(), decrypted);
    }

    @Test
    public void testDecrypt() {
        // Encrypt directly with Cloud KMS
        byte[] cipherText;
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            String resourceName = CryptoKeyName.format(PROJECT, KEY_RING_REGION, KEY_RING, KEY);
            EncryptResponse response = client.encrypt(resourceName, ByteString.copyFrom("abcd".getBytes()));
            cipherText = response.getCiphertext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Use the backend to decrypt
        CloudKMSBackend backend = new CloudKMSBackend();
        byte[] decrypted = backend.decrypt(KEY, cipherText);
        assertArrayEquals("abcd".getBytes(), decrypted);
    }

}
