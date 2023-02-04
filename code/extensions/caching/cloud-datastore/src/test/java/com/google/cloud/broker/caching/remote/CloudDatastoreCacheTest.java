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

import static com.google.cloud.broker.caching.remote.CloudDatastoreCache.*;
import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.TimeUtils;
import com.google.cloud.datastore.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
  "com.sun.org.apache.xerces.*",
  "javax.xml.*",
  "javax.activation.*",
  "org.xml.*",
  "org.w3c.*"
})
@PrepareForTest({TimeUtils.class}) // Classes to be mocked
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
    Query<Entity> query = Query.newEntityQueryBuilder().setKind(CACHE_KIND).build();
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
    KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
    Key key = keyFactory.newKey("test");
    Entity.Builder builder = Entity.newBuilder(key);
    builder.set(CACHE_VALUE_FIELD, BlobValue.of(Blob.copyFrom("abcd".getBytes())));
    builder.set(CACHE_EXPIRY_FIELD, 0);
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
    KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
    Key datastoreKey = keyFactory.newKey("test");
    Entity entity = datastore.get(datastoreKey);
    assertNull(entity);

    // Let the backend set the key/value
    cache.set("test", "abcd".getBytes());

    // Check that the key/value was correctly set
    entity = datastore.get(datastoreKey);
    assertArrayEquals("abcd".getBytes(), entity.getBlob(CACHE_VALUE_FIELD).toByteArray());
  }

  @Test
  public void testSetExpire() {
    // Check that the key doesn't exist
    Datastore datastore = getService();
    KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
    Key datastoreKey = keyFactory.newKey("test");
    Entity entity = datastore.get(datastoreKey);
    assertNull(entity);

    // Mock the system time
    mockStatic(TimeUtils.class);
    long initialNow = 1000000000000L;
    PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(initialNow);

    // Let the backend set the key/value
    int expireIn = 2;
    cache.set("test", "abcd".getBytes(), expireIn);

    // Check that the key/value was correctly set
    entity = datastore.get(datastoreKey);
    assertArrayEquals("abcd".getBytes(), cache.get("test"));
    assertArrayEquals("abcd".getBytes(), entity.getBlob(CACHE_VALUE_FIELD).toByteArray());

    // Change the system time again to simulate elapsing time (up to 1 millisecond before the expiry
    // time)
    long newNow = initialNow + (expireIn * 1000) - 1;
    PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

    // Check that the key/value is still accessible
    entity = datastore.get(datastoreKey);
    assertArrayEquals("abcd".getBytes(), cache.get("test"));
    assertArrayEquals("abcd".getBytes(), entity.getBlob(CACHE_VALUE_FIELD).toByteArray());

    // Change the system time again up to expiry time
    newNow = initialNow + expireIn * 1000;
    PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

    // Check that the key/value is now inaccessible
    assertNull(cache.get("test"));
  }

  @Test
  public void testDelete() {
    // Set a key/value
    Datastore datastore = getService();
    KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
    Key key = keyFactory.newKey("test");
    Entity.Builder builder = Entity.newBuilder(key);
    builder.set(CACHE_VALUE_FIELD, BlobValue.of(Blob.copyFrom("abcd".getBytes())));
    builder.set(CACHE_EXPIRY_FIELD, 0);
    Entity entity = builder.build();
    datastore.put(entity);

    // Let the backend delete the key
    cache.delete("test");

    // Check that the key was deleted
    assertNull(cache.get("test"));
  }

  public void deleteExpiredItems(boolean withLimit) {
    // Mock the system time
    mockStatic(TimeUtils.class);
    long initialNow = 1000000000000L;
    PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(initialNow);

    // Set some keys/values
    Datastore datastore = getService();
    KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
    List<String> keys = Arrays.asList("a", "b", "c", "d", "e");
    List<Integer> expireIns = Arrays.asList(1, 6, 7, 4, 3);
    for (int i = 0; i < keys.size(); i++) {
      cache.set(keys.get(i), "foo".getBytes(), expireIns.get(i));
    }

    // Change the system time again to simulate elapsing time
    long newNow = initialNow + 4 * 1000;
    PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

    // Delete expired items
    List<String> deletedKeys;
    if (withLimit) {
      cache.deleteExpiredItems(2);
      deletedKeys = Arrays.asList("a", "e");
    } else {
      cache.deleteExpiredItems();
      deletedKeys = Arrays.asList("a", "d", "e");
    }

    // Check that the expired items have been deleted
    Query<Entity> query = Query.newEntityQueryBuilder().setKind(CACHE_KIND).build();
    QueryResults<Entity> entities = datastore.run(query);
    int numberItemsLeft = 0;
    while (entities.hasNext()) {
      Entity entity = entities.next();
      assertFalse(deletedKeys.contains(entity.getKey().getName()));
      numberItemsLeft++;
    }
    assertEquals(keys.size() - deletedKeys.size(), numberItemsLeft);
  }

  @Test
  public void testDeleteExpiredItems() {
    // Delete all expired items
    deleteExpiredItems(false);
  }

  @Test
  public void testDeleteExpiredItemsWithLimit() {
    // Only delete the 2 longest expired items
    deleteExpiredItems(true);
  }

  @Test
  public void testLock() throws InterruptedException {
    String LOCK_NAME = "test-lock";
    int WAIT = 20;
    List<String> list = Collections.synchronizedList(new ArrayList<>());

    // Define the concurrent tasks
    Callable<Object> task1 =
        () -> {
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
    Callable<Object> task2 =
        () -> {
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
