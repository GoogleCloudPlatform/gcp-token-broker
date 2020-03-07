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

public class KeysetUtils {

    public static KeysetManager getKeysetManager(String dekUri) {
        if (dekUri.startsWith("gs://")) {
            return new CloudStorageKeysetManager(dekUri);
        }
        else if (dekUri.startsWith("file://")) {
            return new FilesystemKeysetManager(dekUri.substring(7));
        }
        else {
            throw new RuntimeException("Invalid DEK URI: " + dekUri);
        }
    }

}
