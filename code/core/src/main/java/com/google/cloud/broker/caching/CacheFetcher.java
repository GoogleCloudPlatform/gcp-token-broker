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

package com.google.cloud.broker.caching;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.cloud.broker.caching.local.LocalCache;
import com.google.cloud.broker.caching.remote.AbstractRemoteCache;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;


public abstract class CacheFetcher {

    protected boolean allowRemoteCache = true;


    public Object fetch() {
        String cacheKey = getCacheKey();

        // First check in local cache
        Object result = LocalCache.get(cacheKey);
        if (result != null) {
            return result;
        }

        // Not found in local cache, so look in remote cache.
        if (allowRemoteCache) {
            AbstractRemoteCache cache = AbstractRemoteCache.getInstance();
            byte[] encryptedValue = cache.get(cacheKey);
            if (encryptedValue != null) {
                // Cache hit... Let's load the value.
                String json = new String(AbstractEncryptionBackend.getInstance().decrypt(encryptedValue));
                try {
                    result = fromJson(json);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                // Cache miss...
                // Start by acquiring a lock to avoid cache stampede
                Lock lock = cache.acquireLock(cacheKey + "_lock");

                // Check again if there's still no value
                encryptedValue = cache.get(cacheKey);
                if (encryptedValue != null) {
                    // This time it's a cache hit. The value must have been generated
                    // by a competing thread. So we just load the value.
                    String json = new String(AbstractEncryptionBackend.getInstance().decrypt(encryptedValue));
                    try {
                        result = fromJson(json);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    // Compute the result
                    result = computeResult();
                    // Encrypt and cache the value for possible future requests
                    String json = null;
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        json = objectMapper.writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    encryptedValue = AbstractEncryptionBackend.getInstance().encrypt(json.getBytes());
                    cache.set(cacheKey, encryptedValue, getRemoteCacheTime());
                }

                // Release the lock
                lock.unlock();
            }
        }
        else {
            // Remote cache is disabled, so simply compute the result.
            result = computeResult();
        }

        // Add unencrypted value to local cache
        LocalCache.set(cacheKey, result, getLocalCacheTime());

        return result;
    }

    protected abstract String getCacheKey();

    protected abstract int getLocalCacheTime();

    protected abstract int getRemoteCacheTime();

    protected abstract Object computeResult();

    protected abstract Object fromJson(String json) throws IOException;

}
