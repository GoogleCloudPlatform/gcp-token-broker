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

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;


/**
 Dummy database backend that stores data in memory.
 Use only for testing. Do NOT use in production!
 */
public class DummyDatabaseBackend extends AbstractDatabaseBackend {

    private static ConcurrentMap<String, Object> instance;

    private String calculateKey(Model model) {
        return model.getClass().getSimpleName() + "-" + model.getDBId();
    }

    private String calculateKey(Class modelClass, String objectId) {
        return modelClass.getSimpleName() + "-" + objectId;
    }

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        ConcurrentMap<String, Object> cache = getMap();
        String key = calculateKey(modelClass, objectId);
        Model model = (Model) cache.get(key);
        if (model == null) {
            throw new DatabaseObjectNotFound(
                String.format("%s object not found: %s", modelClass.getSimpleName(), objectId));
        }
        return model;
    }

    @Override
    public void save(Model model) {
        if (model.getDBId() == null) {
            model.setDBId(UUID.randomUUID().toString());
        }
        ConcurrentMap<String, Object> cache = getMap();
        String key = calculateKey(model);
        cache.put(key, model);
    }

    @Override
    public void delete(Model model) {
        ConcurrentMap<String, Object> cache = getMap();
        String key = calculateKey(model);
        cache.remove(key);
    }

    @Override
    public void initializeDatabase() {}

    public static ConcurrentMap<String, Object> getMap() {
        if (instance == null) {
            CacheLoader<String, Object> loader;
            loader = new CacheLoader<String, Object>(){
                @Override
                public Object load(String key) throws Exception {
                    return null;
                }
            };
            instance = CacheBuilder.newBuilder().build(loader).asMap();
        }
        return instance;
    }


}
