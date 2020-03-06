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
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.*;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.datastore.*;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class CloudDatastoreCacheTest {

    private static CloudDatastoreCache cache;

    private static Datastore getService() {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        return DatastoreOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @BeforeClass
    public static void setupClass() {
        cache = new CloudDatastoreCache();
    }

    @After
    public void teardown() {
        // Delete all records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind(CloudDatastoreCache.CACHE_KIND).build();
        QueryResults<Entity> entities = datastore.run(query);
        while (entities.hasNext()) {
            Entity entity = entities.next();
            datastore.delete(entity.getKey());
        }
    }

    @Test
    public void testGet() {
        // Create a record in the cache
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CloudDatastoreCache.CACHE_KIND);
        Key key = keyFactory.newKey("test");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set(CloudDatastoreCache.CACHE_VALUE_FIELD, BlobValue.of(Blob.copyFrom("abcd".getBytes())));
        builder.set(CloudDatastoreCache.CACHE_EXPIRY_FIELD, 0);
        Entity entity = builder.build();
        datastore.put(entity);

        // Check that the cache backend can retrieve the value
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
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CloudDatastoreCache.CACHE_KIND);
        Key datastoreKey = keyFactory.newKey("test");
        Entity entity = datastore.get(datastoreKey);
        assertNull(entity);

        // Let the backend set the key/value
        cache.set("test", "abcd".getBytes());

        // Check that the key/value was correctly set
        entity = datastore.get(datastoreKey);
        assertArrayEquals("abcd".getBytes(), entity.getBlob(CloudDatastoreCache.CACHE_VALUE_FIELD).toByteArray());
    }

    @Test
    public void testSetExpire() {
        // Check that the key doesn't exist
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CloudDatastoreCache.CACHE_KIND);
        Key datastoreKey = keyFactory.newKey("test");
        Entity entity = datastore.get(datastoreKey);
        assertNull(entity);

        // Let the backend set the key/value
        cache.set("test", "abcd".getBytes(), 2);

        // Check that the key/value was correctly set
        entity = datastore.get(datastoreKey);
        assertArrayEquals("abcd".getBytes(), cache.get("test"));
        assertArrayEquals("abcd".getBytes(), entity.getBlob(CloudDatastoreCache.CACHE_VALUE_FIELD).toByteArray());

        // Wait for a while
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Check that the key/value is now gone as far as the CloudDatastoreCache backend is concerned
        // (The entity might still be in Datastore though. It's just that its 'expiry' field has expired).
        assertNull(cache.get("test"));
    }

    @Test
    public void testDelete() {
        // Set a key/value
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CloudDatastoreCache.CACHE_KIND);
        Key key = keyFactory.newKey("test");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set(CloudDatastoreCache.CACHE_VALUE_FIELD, BlobValue.of(Blob.copyFrom("abcd".getBytes())));
        builder.set(CloudDatastoreCache.CACHE_EXPIRY_FIELD, 0);
        Entity entity = builder.build();
        datastore.put(entity);

        // Let the backend delete the key
        cache.delete("test");

        // Check that the key was deleted
        assertNull(cache.get("test"));
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
