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

package com.google.cloud.broker.database.backends;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.datastore.*;
import org.junit.After;
import org.junit.Test;

import com.google.cloud.broker.settings.AppSettings;


public class CloudDatastoreBackendTest {

    // TODO: Still needs tests:
    // - Error when saving or deleting

    @After
    public void teardown() {
        // Delete all records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("Foo").build();
        QueryResults<Entity> entities = datastore.run(query);
        while (entities.hasNext()) {
            Entity entity = entities.next();
            datastore.delete(entity.getKey());
        }
    }

    private static Datastore getService() {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        return DatastoreOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    private static void assertListEqualsListValue(List<String> list, ListValue listValue) {
        List<String> l = new LinkedList<>();
        for (Value<?> v : listValue.get()) {
            l.add((String) v.get());
        }
        assertEquals(list, l);
    }
    
    /**
     * Test saving a new model to the database.
     */
    @Test
    public void testSaveNew() {
        // Check that there are no records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("Foo").build();
        QueryResults<Entity> entities = datastore.run(query);
        assertFalse(entities.hasNext());

        // Create a new record
        HashMap<String, Object> values = new HashMap<String, Object>();
        Foo foo = new Foo("myid", "abcd".getBytes(), 1564094282994L, List.of("hello", "hi"));
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(foo);

        // Check that the record was correctly created
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("Foo");
        Key key = keyFactory.newKey("myid");
        Entity entity = datastore.get(key);
        assertEquals(1564094282994L, entity.getValue("longVal").get());
        assertArrayEquals("abcd".getBytes(), ((Blob) entity.getValue("byteVal").get()).toByteArray());
        assertListEqualsListValue(List.of("hello", "hi"), entity.getValue("stringList"));
    }

    /**
     * Test updating an existing model to the database.
     */
    @Test
    public void testUpdate() {
        // Create a record in the database
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("Foo");
        Key key = keyFactory.newKey("myid");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("longVal", 1564094282994L);
        builder.set("byteVal", Blob.copyFrom("abcd".getBytes()));
        Entity entity = builder.build();
        datastore.put(entity);

        // Update the record with the same ID but different values
        HashMap<String, Object> values = new HashMap<String, Object>();
        Foo foo = new Foo("myid", "xyz".getBytes(), 2222222222222L, List.of("hello", "hi"));
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(foo);

        // Check that the record was updated
        keyFactory = datastore.newKeyFactory().setKind("Foo");
        key = keyFactory.newKey("myid");
        entity = datastore.get(key);
        assertEquals(2222222222222L, entity.getValue("longVal").get());
        assertArrayEquals("xyz".getBytes(), ((Blob) entity.getValue("byteVal").get()).toByteArray());
        assertListEqualsListValue(List.of("hello", "hi"), entity.getValue("stringList"));
    }

    /**
     * Test saving a model to the database, without specifying an ID. A ID should automatically be assigned.
     */
    @Test
    public void testSaveWithoutID() {
        // Check that there are no records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("Foo").build();
        QueryResults<Entity> entities = datastore.run(query);
        assertFalse(entities.hasNext());

        // Create a new record without specifying an ID
        Foo foo = new Foo(null, "abcd".getBytes(), 1564094282994L, List.of("hello", "hi"));
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(foo);

        // Check that the record was correctly created
        query = Query.newEntityQueryBuilder().setKind("Foo").build();
        entities = datastore.run(query);
        Entity entity = entities.next();
        assertEquals(1564094282994L, entity.getValue("longVal").get());
        assertArrayEquals("abcd".getBytes(), ((Blob) entity.getValue("byteVal").get()).toByteArray());
        assertListEqualsListValue(List.of("hello", "hi"), entity.getValue("stringList"));
        assertFalse(entities.hasNext());

        // Check that the ID is a valid UUID
        UUID uuid = UUID.fromString(entity.getKey().getName());
        assertEquals(uuid.toString(), entity.getKey().getName());
    }

    /**
     * Test retrieving a model from the database.
     */
    @Test
    public void testGet() {
        // Create a record in the database
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("Foo");
        Key key = keyFactory.newKey("myid");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("longVal", 1564094282994L);
        builder.set("byteVal", Blob.copyFrom("abcd".getBytes()));
        builder.set("stringList", "hello", "hi");
        Entity entity = builder.build();
        datastore.put(entity);

        // Check that the record is correctly retrieved
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        Foo foo = (Foo) backend.get(Foo.class, "myid");
        assertEquals("myid", foo.getId());
        assertEquals(1564094282994L, foo.getLongVal().longValue());
        assertArrayEquals("abcd".getBytes(), foo.getByteVal());
        assertEquals(List.of("hello", "hi"), foo.getStringList());
    }

    /**
     * Test retrieving a model that doesn't exist. The DatabaseObjectNotFound exception should be thrown.
     */
    @Test(expected = DatabaseObjectNotFound.class)
    public void testGetNotExist() {
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.get(Foo.class, "whatever");
    }

    /**
     * Test deleting a model from the database.
     */
    @Test
    public void testDelete() {
        // Create a record in the database
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("Foo");
        Key key = keyFactory.newKey("myid");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("longVal", 1564094282994L);
        builder.set("byteVal", Blob.copyFrom("abcd".getBytes()));
        Entity entity = builder.build();
        datastore.put(entity);

        // Delete the record
        Foo foo = new Foo("myid", null, null, List.of("hello", "hi"));
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.delete(foo);

        // Check that the record was deleted
        keyFactory = datastore.newKeyFactory().setKind("Foo");
        key = keyFactory.newKey("myid");
        entity = datastore.get(key);
        assertNull(entity);
    }

    @Test
    public void testInitializeDatabase() {
        // Just a smoke test. Cloud Datastore doesn't need initializing.
    }
}
