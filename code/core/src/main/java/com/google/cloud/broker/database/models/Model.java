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

package com.google.cloud.broker.database.models;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public abstract class Model {

  public abstract Map<String, Object> toMap();

  public abstract void setDBId(String id);

  public abstract String getDBId();

  public static Model fromMap(Class<?> klass, Map<String, Object> map) {
    Method method;
    try {
      method = klass.getMethod("fromMap", Map.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    try {
      return (Model) method.invoke(null, map);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
