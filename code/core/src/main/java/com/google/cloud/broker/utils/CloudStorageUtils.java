package com.google.cloud.broker.utils;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class CloudStorageUtils {

    private static final String GCS_API = "https://www.googleapis.com/auth/devstorage.read_write";

    public static Storage getCloudStorageClient() {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault().createScoped(GCS_API);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return StorageOptions.newBuilder()
            .setCredentials(credentials)
            .build()
            .getService();
    }

}
