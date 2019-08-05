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

package com.google.cloud.broker.database.backends;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.UUID;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.datastore.*;
import org.junit.After;
import org.junit.Test;


import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.settings.AppSettings;


public class CloudDatastoreBackendTest {

    // TODO: Still needs tests:
    // - Error when saving or deleting

    @After
    public void teardown() {
        // Delete all records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("RefreshToken").build();
        QueryResults<Entity> entities = datastore.run(query);
        while (entities.hasNext()) {
            Entity entity = entities.next();
            datastore.delete(entity.getKey());
        }
    }

    private static Datastore getService() {
        AppSettings settings = AppSettings.getInstance();
        String projectId = settings.getProperty("GCP_PROJECT");
        return DatastoreOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    /**
     * Test saving a new model to the database.
     */
    @Test
    public void testSaveNew() {
        // Check that there are no records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("RefreshToken").build();
        QueryResults<Entity> entities = datastore.run(query);
        assertFalse(entities.hasNext());

        // Create a new record
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creation_time", 1564094282994L);
        values.put("value", "abcd".getBytes());
        RefreshToken token = new RefreshToken(values);
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(token);

        // Check that the record was correctly created
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        Key key = keyFactory.newKey("alice@example.com");
        Entity entity = datastore.get(key);
        assertEquals(entity.getValue("creation_time").get(), 1564094282994L);
        assertArrayEquals(((Blob) entity.getValue("value").get()).toByteArray(), "abcd".getBytes());
    }

    /**
     * Test updating an existing model to the database.
     */
    @Test
    public void testUpdate() {
        // Create a record in the database
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        Key key = keyFactory.newKey("alice@example.com");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("creation_time", 1564094282994L);
        builder.set("value", Blob.copyFrom("abcd".getBytes()));
        Entity entity = builder.build();
        datastore.put(entity);

        // Update the record with the same ID but different values
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creation_time", 2222222222222L);
        values.put("value", "xyz".getBytes());
        RefreshToken token = new RefreshToken(values);
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(token);

        // Check that the record was updated
        keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        key = keyFactory.newKey("alice@example.com");
        entity = datastore.get(key);
        assertEquals(entity.getValue("creation_time").get(), 2222222222222L);
        assertArrayEquals(((Blob) entity.getValue("value").get()).toByteArray(), "xyz".getBytes());
    }

    /**
     * Test saving a model to the database, without specifying an ID. A ID should automatically be assigned.
     */
    @Test
    public void testSaveWithoutID() {
        // Check that there are no records
        Datastore datastore = getService();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind("RefreshToken").build();
        QueryResults<Entity> entities = datastore.run(query);
        assertFalse(entities.hasNext());

        // Create a new record without specifying an ID
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("creation_time", 1564094282994L);
        values.put("value", "abcd".getBytes());
        RefreshToken token = new RefreshToken(values);
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.save(token);

        // Check that the record was correctly created
        query = Query.newEntityQueryBuilder().setKind("RefreshToken").build();
        entities = datastore.run(query);
        Entity entity = entities.next();
        assertEquals(entity.getValue("creation_time").get(), 1564094282994L);
        assertArrayEquals(((Blob) entity.getValue("value").get()).toByteArray(), "abcd".getBytes());
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
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        Key key = keyFactory.newKey("alice@example.com");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("creation_time", 1564094282994L);
        builder.set("value", Blob.copyFrom("abcd".getBytes()));
        Entity entity = builder.build();
        datastore.put(entity);

        // Check that the record is correctly retrieved
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        RefreshToken token = (RefreshToken) backend.get(RefreshToken.class, "alice@example.com");
        assertEquals(token.getValue("id"), "alice@example.com");
        assertEquals(token.getValue("creation_time"), 1564094282994L);
        assertArrayEquals((byte[]) token.getValue("value"), "abcd".getBytes());
    }

    /**
     * Test retrieving a model that doesn't exist. The DatabaseObjectNotFound exception should be thrown.
     */
    @Test(expected = DatabaseObjectNotFound.class)
    public void testGetNotExist() {
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.get(RefreshToken.class, "whatever");
    }

    /**
     * Test deleting a model from the database.
     */
    @Test
    public void testDelete() {
        // Create a record in the database
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        Key key = keyFactory.newKey("alice@example.com");
        Entity.Builder builder = Entity.newBuilder(key);
        builder.set("creation_time", 1564094282994L);
        builder.set("value", Blob.copyFrom("abcd".getBytes()));
        Entity entity = builder.build();
        datastore.put(entity);

        // Delete the record
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        RefreshToken token = new RefreshToken(values);
        CloudDatastoreBackend backend = new CloudDatastoreBackend();
        backend.delete(token);

        // Check that the record was deleted
        keyFactory = datastore.newKeyFactory().setKind("RefreshToken");
        key = keyFactory.newKey("alice@example.com");
        entity = datastore.get(key);
        assertNull(entity);
    }

    @Test
    public void testInitializeDatabase() {
        // Just a smoke test. Cloud Datastore doesn't need initializing.
    }
}
