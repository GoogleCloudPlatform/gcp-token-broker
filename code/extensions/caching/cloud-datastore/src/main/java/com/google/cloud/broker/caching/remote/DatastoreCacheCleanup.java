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

package com.google.cloud.broker.caching.remote;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatastoreCacheCleanup {

    private static final Class<?> klass = MethodHandles.lookup().lookupClass();
    private static final Logger logger = LoggerFactory.getLogger(klass);

    public static void main(String[] args) {
        Integer limit = null;
        if (args.length > 0) {
            limit = Integer.parseInt(args[0]);
        }
        CloudDatastoreCache cache = new CloudDatastoreCache();
        int numDeletedItems = cache.deleteExpiredItems(limit);
        logger.info(klass.getSimpleName() + " - Deleted expired item(s): " + numDeletedItems);
    }

}
