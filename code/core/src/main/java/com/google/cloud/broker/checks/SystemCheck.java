// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.checks;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import com.google.cloud.broker.caching.remote.AbstractRemoteCache;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemCheck {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static List<CheckResult> results = new ArrayList<>();

    public static void runChecks() {
        // Check the database connection
        CheckResult dbResult = AbstractDatabaseBackend.getInstance().checkConnection();
        dbResult.setType("Database backend");
        results.add(dbResult);

        // Check the cache connection
        CheckResult cacheResult = AbstractRemoteCache.getInstance().checkConnection();
        cacheResult.setType("Cache backend");
        results.add(cacheResult);

        // Check the encryption backend
        CheckResult encryptionResult = AbstractEncryptionBackend.getInstance().checkConnection();
        encryptionResult.setType("Encryption backend");
        results.add(encryptionResult);

        // Collate all the potential check failures together
        StringBuilder sb = new StringBuilder();
        for (CheckResult result : results) {
            if (!result.isSuccess()) {
                sb.append(String.format("* Failure: %s\n\n%s\n\n", result.getType(), result.getMessage()));
            }
        }

        // Raise exception if any failures were found
        if (sb.length() > 0) {
            throw new IllegalStateException("System check failures!\n\n" + sb.toString());
        }

        logger.info("System checks passed");
    }

    public static void main(String[] args) {
        runChecks();
    }

}
