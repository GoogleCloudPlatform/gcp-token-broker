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
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.crypto.tink.proto.Keyset;

/**
 * KeysetManager that reads and writes DEKs from the local filesystem.
 */
public class FilesystemKeysetManager extends KeysetManager {

    private Path dekUri;

    FilesystemKeysetManager(String dekUri) {
        this.dekUri = Paths.get(dekUri);
    }

    @Override
    public Keyset read() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public EncryptedKeyset readEncrypted() throws IOException {
        return JsonKeysetReader
            .withPath(dekUri)
            .readEncrypted();
    }

    @Override
    public void write(Keyset keyset) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(EncryptedKeyset keyset) throws IOException {
        JsonKeysetWriter
            .withPath(dekUri)
            .write(keyset);
    }

}
