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

package com.google.cloud.broker.authentication.backends;

import java.lang.reflect.Constructor;

import com.google.cloud.broker.settings.AppSettings;


public abstract class AbstractAuthenticationBackend {

    private static AbstractAuthenticationBackend instance;

    public static AbstractAuthenticationBackend getInstance() {
        AppSettings settings = AppSettings.getInstance();
        if (instance == null) {
            try {
                String className = settings.getProperty("AUTHENTICATION_BACKEND");
                if (className == null) {
                    throw new RuntimeException("The `AUTHENTICATION_BACKEND` setting is not set");
                }
                Class c = Class.forName(className);
                Constructor constructor = c.getConstructor();
                instance = (AbstractAuthenticationBackend) constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public abstract String authenticateUser();
}