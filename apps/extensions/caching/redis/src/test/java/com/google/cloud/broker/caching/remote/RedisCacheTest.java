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

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.config.Config;

import java.util.concurrent.locks.Lock;


public class RedisCacheTest {

    private static RedissonClient client;
    private static RedisCache backend;

    @BeforeClass
    public static void setupClass() {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://localhost:6379")).setDatabase(0);
        client = Redisson.create(config);
        backend = new RedisCache();
    }

    @After
    public void teardown() {
        // Clear the database
        RKeys keys = client.getKeys();
        keys.flushdb();
    }

    @Test
    public void testGet() {
        // Set a key/value
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        bucket.set("abcd".getBytes());

        // Check that the backend can retrieve the key/value
        assertArrayEquals("abcd".getBytes(), backend.get("test"));
    }

    @Test
    public void testGetNotExist() {
        // Check that null is returned when the key doesn't exist
        assertNull(backend.get("whatever"));
    }

    @Test
    public void testSet() {
        // Check that the key doesn't exist
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        assertNull(bucket.get());

        // Let the backend set the key/value
        backend.set("test", "abcd".getBytes());

        // Check that the key/value was correctly set
        assertArrayEquals("abcd".getBytes(), bucket.get());
    }

    @Test
    public void testSetExpire() {
        // Check that the key doesn't exist
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        assertNull(bucket.get());

        // Let the backend set the key/value
        backend.set("test", "abcd".getBytes(), 1);

        // Check that the key/value was correctly set
        assertArrayEquals("abcd".getBytes(), bucket.get());

        // Wait for a second
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Check that the key/value is now gone
        assertNull(bucket.get());
    }

    @Test
    public void testDelete() {
        // Set a key/value
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        bucket.set("abcd".getBytes());

        // Let the backend delete the key
        backend.delete("test");

        // Check that the key was deleted
        assertNull(bucket.get());
    }

    @Test
    public void testLock() {
        Lock lock = backend.acquireLock("test-lock");
        lock.unlock();
    }
}