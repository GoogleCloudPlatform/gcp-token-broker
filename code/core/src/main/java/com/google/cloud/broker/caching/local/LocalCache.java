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

package com.google.cloud.broker.caching.local;

import java.util.concurrent.TimeUnit;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

public class LocalCache {

  private static ExpiringMap<String, Object> cache =
      ExpiringMap.builder().variableExpiration().build();

  public static Object get(String key) {
    return cache.get(key);
  }

  public static void set(String key, Object value) {
    cache.put(key, value);
  }

  public static void set(String key, Object value, int expireIn) {
    cache.put(key, value, ExpirationPolicy.CREATED, expireIn, TimeUnit.SECONDS);
  }

  public static void delete(String key) {
    cache.remove(key);
  }
}
