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

package com.google.cloud.broker.database.backends.experimental;

import java.util.concurrent.ExecutionException;

import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.database.models.Model;

/**
 * Database backend that uses local memory and a Guava LoadingCache instead of an external database.
 * This is an experimental backend. It might be removed or changed in backwards-compatible ways in the future.
 * Do NOT use in production!
 * */

public class LocalMemoryDatabaseBackend extends AbstractDatabaseBackend {

    private LoadingCache<String,Object> cache;

    public LocalMemoryDatabaseBackend(){
        cache = CacheBuilder.newBuilder()
                .build(CacheLoader.from(new Supplier<Object>() { @Override public Object get() { return null; } }));
    }

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        try {
            return (Model) cache.get(objectId);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(Model model) {
        cache.put((String) model.getValue("id"), model);
    }

    @Override
    public void delete(Model model) {
        cache.invalidate((String) model.getValue("id"));
    }
}