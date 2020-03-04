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

package com.google.cloud.broker.encryption.backends.keyset;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.crypto.tink.proto.Keyset;

import com.google.cloud.broker.utils.CloudStorageUtils;


/**
 * KeysetManager that reads and writes DEKs from Cloud Storage.
 */
public class CloudStorageKeysetManager extends KeysetManager {

    private URI dekUri;

    CloudStorageKeysetManager(String dekUri) {
        try {
            this.dekUri = new URI(dekUri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Keyset read() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public EncryptedKeyset readEncrypted() throws IOException {
        BlobId blobId = BlobId.of(dekUri.getAuthority(), dekUri.getPath().substring(1));
        return JsonKeysetReader
            .withBytes(CloudStorageUtils.getCloudStorageClient().readAllBytes(blobId))
            .readEncrypted();
    }

    @Override
    public void write(Keyset keyset) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(EncryptedKeyset keyset) throws IOException {
        BlobId blobId = BlobId.of(dekUri.getAuthority(), dekUri.getPath().substring(1));
        WriteChannel wc = CloudStorageUtils.getCloudStorageClient().writer(BlobInfo.newBuilder(blobId).build());
        OutputStream os = Channels.newOutputStream(wc);
        JsonKeysetWriter
            .withOutputStream(os)
            .write(keyset);
        os.close();
        wc.close();
    }

}