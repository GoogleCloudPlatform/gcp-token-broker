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

import java.util.*;

import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.datastore.*;

import com.google.cloud.broker.database.DatabaseObjectNotFound;


public class CloudDatastoreBackend extends AbstractDatabaseBackend {

    private Datastore getService() {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
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
                if (value instanceof BlobValue) {
                    values.put(name, ((Blob) value.get()).toByteArray());
                }
                else if(value instanceof ListValue) {
                    List<Object> list = new LinkedList<>();
                    for (Value<?> v: ((ListValue) value).get()) {
                        list.add(v.get());
                    }
                    values.put(name, list);
                }
                else {
                    values.put(name, value.get());
                }
            }
        }

        // Instantiate a new object
        return Model.fromMap(modelClass, values);
    }

    public void save(Model model) {
        if (model.getDBId() == null) {
            model.setDBId(UUID.randomUUID().toString());
        }
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(model.getClass().getSimpleName());
        Key key = keyFactory.newKey(model.getDBId());
        Entity.Builder builder = Entity.newBuilder(key);
        Map<String, Object> map = model.toMap();
        for(Map.Entry<String, Object> entry : map.entrySet()) {
            String name = entry.getKey();
            Value<?> value = objectToValue(entry.getValue());
            builder.set(name, value);
        }
        Entity entity = builder.build();
        datastore.put(entity);
    }

    // Converts an Object to a Datastore Value
    private Value<?> objectToValue(Object object) {
        if (object instanceof String) {
            return StringValue.of((String) object);
        }
        else if (object instanceof Long) {
            return LongValue.of((long) object);
        }
        else if (object instanceof byte[]) {
            return BlobValue.of(Blob.copyFrom((byte[]) object));
        }
        else if (object instanceof List<?>) {
            List<Value<?>> list = new LinkedList<>();
            for (Object o : (List<?>) object) {
                Value<?> valueObject = objectToValue(o);
                list.add(valueObject);
            }
            return ListValue.of(list);
        }
        else {
            // TODO extend to other supported types
            throw new UnsupportedOperationException("Unsupported type: " + object.getClass());
        }
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
