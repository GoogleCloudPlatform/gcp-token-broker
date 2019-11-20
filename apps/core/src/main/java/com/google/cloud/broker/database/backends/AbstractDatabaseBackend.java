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

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.utils.InstanceUtils;


public abstract class AbstractDatabaseBackend {

    private static AbstractDatabaseBackend instance;

    public abstract Model get(Class modelClass, String objectId)  throws DatabaseObjectNotFound;
    public abstract void save(Model model);
    public abstract void delete(Model model);
    public abstract void initializeDatabase();

    public static AbstractDatabaseBackend getInstance() {
        String className = AppSettings.getInstance().getString(AppSettings.DATABASE_BACKEND);
        if (instance == null || !className.equals(instance.getClass().getCanonicalName())) {
            instance = (AbstractDatabaseBackend) InstanceUtils.invokeConstructor(className);
        }
        return instance;
    }

}
