// Copyright 2019 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.caching.local;

import com.google.api.client.util.Clock;

import java.util.HashMap;

public class LocalCache {

    // TODO: Add process to prune expired records from memory

    private static HashMap<String, Object> CACHE = new HashMap<String, Object>();
    private static HashMap<String, Long> EXPIRY_TIMES = new HashMap<String, Long>();

    private static final Long NEVER = -1L;

    public static Object get(String key) {
        Object value = CACHE.get(key);
        if (value == null) {
            return null;
        }
        else {
            long expiryTime = EXPIRY_TIMES.get(key);
            long now = Clock.SYSTEM.currentTimeMillis();
            if ((expiryTime == NEVER) || (expiryTime > now)) {
                return value;
            }
            else {
                return null;
            }
        }
    }

    public static void set(String key, Object value) {
        CACHE.put(key, value);
        EXPIRY_TIMES.put(key, NEVER);
    }

    public static void set(String key, Object value, int expireIn) {
        long now = Clock.SYSTEM.currentTimeMillis();
        CACHE.put(key, value);
        EXPIRY_TIMES.put(key, now + 1000 * expireIn);
    }

    public static void delete(String key) {
        CACHE.remove(key);
        EXPIRY_TIMES.remove(key);
    }

}
