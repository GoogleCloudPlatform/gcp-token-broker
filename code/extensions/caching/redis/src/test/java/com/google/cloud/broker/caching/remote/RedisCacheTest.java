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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.*;
import org.junit.*;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.config.Config;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;

public class RedisCacheTest {

    private static RedissonClient client;
    private static RedisCache cache;

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.REDIS_CACHE_HOST, "localhost",
        AppSettings.REDIS_CACHE_PORT, 6379,
        AppSettings.REDIS_CACHE_DB, 0
    ));

    @BeforeClass
    public static void setupClass() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setDatabase(0);
        client = Redisson.create(config);
        cache = new RedisCache();
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
        assertArrayEquals("abcd".getBytes(), cache.get("test"));
    }

    @Test
    public void testGetNotExist() {
        // Check that null is returned when the key doesn't exist
        assertNull(cache.get("whatever"));
    }

    @Test
    public void testSet() {
        // Check that the key doesn't exist
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        assertNull(bucket.get());

        // Let the backend set the key/value
        cache.set("test", "abcd".getBytes());

        // Check that the key/value was correctly set
        assertArrayEquals("abcd".getBytes(), bucket.get());
    }

    @Test
    public void testSetExpire() {
        // Check that the key doesn't exist
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        assertNull(bucket.get());

        // Let the backend set the key/value
        int expireIn = 1;
        cache.set("test", "abcd".getBytes(), expireIn);

        // Check that the key/value was correctly set
        assertArrayEquals("abcd".getBytes(), bucket.get());

        // Wait for a while
        try {
            Thread.sleep(expireIn * 1000 + 1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Check that the key/value is now gone
        assertNull(cache.get("test"));
        assertNull(bucket.get());
    }

    @Test
    public void testDelete() {
        // Set a key/value
        RBucket<byte[]> bucket = client.getBucket("test", ByteArrayCodec.INSTANCE);
        bucket.set("abcd".getBytes());

        // Let the backend delete the key
        cache.delete("test");

        // Check that the key was deleted
        assertNull(cache.get("test"));
        assertNull(bucket.get());
    }

    @Test
    public void testLock() throws InterruptedException {
        String LOCK_NAME = "test-lock";
        int WAIT = 20;
        List<String> list = Collections.synchronizedList(new ArrayList<>());

        // Define the concurrent tasks
        Callable<Object> task1 = () -> {
            Lock lock = cache.acquireLock(LOCK_NAME);
            Thread.sleep(WAIT);
            list.add("a1");
            Thread.sleep(WAIT);
            list.add("b1");
            Thread.sleep(WAIT);
            list.add("c1");
            lock.unlock();
            return null;
        };
        Callable<Object> task2 = () -> {
            Lock lock = cache.acquireLock(LOCK_NAME);
            Thread.sleep(WAIT);
            list.add("a2");
            Thread.sleep(WAIT);
            list.add("b2");
            Thread.sleep(WAIT);
            list.add("c2");
            lock.unlock();
            return null;
        };
        List<Callable<Object>> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);

        // Execute the tasks concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.invokeAll(tasks);

        // Verify that the tasks updated the shared list in sequence
        String result = String.join(",", list);
        assertTrue(result.equals("a1,b1,c1,a2,b2,c2") || result.equals("a2,b2,c2,a1,b1,c1"));
    }
}