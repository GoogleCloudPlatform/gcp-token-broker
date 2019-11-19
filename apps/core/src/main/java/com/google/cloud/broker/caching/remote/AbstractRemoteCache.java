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

package com.google.cloud.broker.caching.remote;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.InstanceUtils;

import java.util.concurrent.locks.Lock;

public abstract class AbstractRemoteCache {

    private static AbstractRemoteCache instance;

    public abstract byte[] get(String key);
    public abstract void set(String key, byte[] value);
    public abstract void set(String key, byte[] value, int expireIn);  // "expireIn" in seconds
    public abstract void delete(String key);
    public abstract Lock acquireLock(String lockName);


    public static AbstractRemoteCache getInstance() {
        if (instance == null) {
            String className = AppSettings.getProperty(AppSettings.REMOTE_CACHE, "com.google.cloud.broker.caching.remote.RedisCache");
            instance = (AbstractRemoteCache) InstanceUtils.invokeConstructor(className);
        }
        return instance;
    }
}
