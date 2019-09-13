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

package com.google.cloud.broker.accesstokens.providers;

import com.google.cloud.broker.accesstokens.AccessToken;
import com.google.cloud.broker.settings.AppSettings;

import java.lang.reflect.Constructor;

public abstract class AbstractProvider {

    private static AbstractProvider instance;

    public static AbstractProvider getInstance() {
        if (instance == null) {
            try {
                String className = AppSettings.getProperty("PROVIDER", "com.google.cloud.broker.accesstokens.providers.RefreshTokenProvider");
                Class c = Class.forName(className);
                Constructor constructor  = c.getConstructor();
                instance = (AbstractProvider) constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public abstract AccessToken getAccessToken(String owner, String scope);

}
