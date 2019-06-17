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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import com.google.cloud.datastore.*;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;


public class CloudDatastoreBackend extends AbstractDatabaseBackend {

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        // Load entity from Datastore
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(modelClass.getSimpleName());
        Key key = keyFactory.newKey(objectId);
        Entity entity = datastore.get(key);

        // No entity found
        if (entity == null) {
            throw new DatabaseObjectNotFound(
                String.format("%s object not found: %s", modelClass.getSimpleName(), objectId));
        }

        // Load entity values into a hashmap
        HashMap<String, Object> values = new HashMap<>();
        for (String name : entity.getNames()) {
            Value<?> value = entity.getValue(name);
            if (value != null) {
                values.put(name, value.get());
            }
        }

        // Instantiate a new object
        Model model = null;
        try {
            Constructor constructor = modelClass.getConstructor(HashMap.class);
            model = (Model) constructor.newInstance(values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return model;
    }

    @Override
    public void insert(Model model) {
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        FullEntity.Builder builder = FullEntity.newBuilder(keyFactory.newKey());

        for(Map.Entry<String, Object> entry : model.getValues().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                builder.set(name, (String) value);
            }
            else if (value instanceof Long) {
                builder.set(name, (long) value);
            }
            else {
                // TODO extend to other supported types
                throw new RuntimeException("Unsupported type");
            }
        }

        FullEntity<IncompleteKey> entity = builder.build();
        Key key = datastore.add(entity).getKey();
        model.setValue("id", key.getName());
    }

    @Override
    public void update(Model model) {
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        Key key = keyFactory.newKey(model.getValue("id").toString());
        Entity.Builder builder = Entity.newBuilder(key);

        for(Map.Entry<String, Object> entry : model.getValues().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                builder.set(name, (String) value);
            }
            else if (value instanceof Long) {
                builder.set(name, (long) value);
            }
            else {
                // TODO extend to other supported types
                throw new RuntimeException("Unsupported type");
            }
        }

        Entity entity = builder.build();
        datastore.update(entity);
    }

    @Override
    public void delete(Model model) {
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        Key key = keyFactory.newKey(model.getValue("id").toString());
        datastore.delete(key);
    }

    @Override
    public void initializeDatabase() {
        // Cloud Datastore doesn't need any initialization. A table is automatically be created
        // when the first object is inserted.
    }
}
