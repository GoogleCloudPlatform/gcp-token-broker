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

/**
 * Command-line utility used to generate a new key stored in GCS
 */
public class KeyGen {
    private static String USAGE = "<bucket> <name> <cryptoKey>";

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(USAGE);
            System.exit(1);
        }

        String bucket = args[0];
        String name = args[1];
        String cryptoKey = args[2];
        genKey(bucket, name, cryptoKey);
    }

    public static void genKey(String bucket, String name, String cryptoKey) {
        System.out.println("Generating key");
        System.out.println("Wrapping with " + cryptoKey);
        System.out.println("Writing to gs://" + bucket + "/" + name);
        try {
            KMSEnvelopeEncryptionBackend.generateAndWrite(bucket, name, cryptoKey);
        } catch (Exception e) {
            System.err.println("Failed to generate and write key");
            e.printStackTrace(System.err);
            System.exit(1);
        }
        System.out.println("Done.");
    }
}
