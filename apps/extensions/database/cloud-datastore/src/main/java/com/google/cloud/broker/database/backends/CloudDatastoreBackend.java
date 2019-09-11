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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.datastore.*;

import com.google.cloud.broker.database.DatabaseObjectNotFound;


public class CloudDatastoreBackend extends AbstractDatabaseBackend {

    private Datastore getService() {
        String projectId = AppSettings.requireProperty("GCP_PROJECT");
        return DatastoreOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        // Load entity from Datastore
        Datastore datastore = getService();
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
        values.put("id", objectId);
        for (String name : entity.getNames()) {
            Value<?> value = entity.getValue(name);
            if (value != null) {
                if (value.get() instanceof Blob) {
                    values.put(name, ((Blob) value.get()).toByteArray());
                }
                else {
                    values.put(name, value.get());
                }

            }
        }

        // Instantiate a new object
        return Model.fromHashMap(modelClass, values);
    }

    public void save(Model model) {
        if (model.getDBId() == null) {
            model.setDBId(UUID.randomUUID().toString());
        }

        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        Key key = keyFactory.newKey(model.getDBId());
        Entity.Builder builder = Entity.newBuilder(key);
        HashMap<String, Object> hashmap = model.toHashMap();
        for(Map.Entry<String, Object> entry : hashmap.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                builder.set(name, (String) value);
            }
            else if (value instanceof Long) {
                builder.set(name, (long) value);
            }
            else if (value instanceof byte[]) {
                builder.set(name, Blob.copyFrom((byte[]) value));
            }
            else {
                // TODO extend to other supported types
                throw new UnsupportedOperationException("Unsupported type: " + value.getClass());
            }
        }
        Entity entity = builder.build();
        datastore.put(entity);
    }

    @Override
    public void delete(Model model) {
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        Key key = keyFactory.newKey(model.getDBId());
        datastore.delete(key);
    }

    @Override
    public void initializeDatabase() {
        // Cloud Datastore doesn't need to do any initialization. A table is automatically be created
        // when the first object is inserted.
    }
}
