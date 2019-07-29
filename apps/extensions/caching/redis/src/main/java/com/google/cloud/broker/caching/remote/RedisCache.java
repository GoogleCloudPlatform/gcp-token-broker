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

package com.google.cloud.broker.caching.remote;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.config.Config;

import com.google.cloud.broker.settings.AppSettings;


public class RedisCache extends AbstractRemoteCache {

    RedissonClient client;

    public RedisCache() {
        AppSettings settings = AppSettings.getInstance();
        String host = settings.getProperty("REDIS_CACHE_HOST", "localhost");
        Integer port = Integer.valueOf(settings.getProperty("REDIS_CACHE_PORT", "6379"));
        Config config = new Config();
        config.useSingleServer()
            .setAddress(String.format("redis://%s:%s", host, port))
            .setDatabase(Integer.valueOf(settings.getProperty("REDIS_CACHE_DB", "0")));
        client = Redisson.create(config);
    }

    public byte[] get(String key) {
        RBucket<byte[]> bucket = client.getBucket(key, ByteArrayCodec.INSTANCE);
        return bucket.get();
    }

    public void set(String key, byte[] value) {
        RBucket<byte[]> bucket = client.getBucket(key, ByteArrayCodec.INSTANCE);
        bucket.set(value);
    }

    public void set(String key, byte[] value, int expireIn) {
        RBucket<byte[]> bucket = client.getBucket(key, ByteArrayCodec.INSTANCE);
        bucket.set(value, expireIn, TimeUnit.SECONDS);
    }

    public void delete(String key) {
        RBucket<byte[]> bucket = client.getBucket(key, ByteArrayCodec.INSTANCE);
        bucket.delete();
    }

    public Lock acquireLock(String lockName) {
        RLock lock = client.getLock(lockName);
        lock.lock();
        return lock;
    }

}
