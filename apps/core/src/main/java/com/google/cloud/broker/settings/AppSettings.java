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

package com.google.cloud.broker.settings;

import com.google.cloud.broker.utils.EnvUtils;

import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Constructor;

public class AppSettings {

    private static Properties instance = null;
    public static final String ENCRYPTION_CRYPTO_KEY = "ENCRYPTION_CRYPTO_KEY";
    public static final String ENCRYPTION_DEK_URI = "ENCRYPTION_DEK_URI";
    public static final String TEST_ENCRYPTION = "mem";
    public static final String NO_ENCRYPTION = "plaintext";


    public AppSettings() {}

    private static void loadEnvironmentSettings() {
        // Override default settings with potential environment variables
        Map<String, String> env = EnvUtils.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().startsWith("APP_SETTING_")) {
                setProperty(entry.getKey().substring("APP_SETTING_".length()), entry.getValue());
            }
        }
    }

    private static Properties getInstance() {
        if (instance == null) {
            String settingsClassName = EnvUtils.getenv().get("APP_SETTINGS_CLASS");
            if (settingsClassName == null) {
                instance = new Properties();
            }
            else {
                try {
                    Class c = Class.forName(settingsClassName);
                    Constructor constructor = c.getConstructor();
                    instance = (Properties) constructor.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            loadEnvironmentSettings();
        }
        return instance;
    }

    public static String getProperty(String key) {
        return getInstance().getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return getInstance().getProperty(key, defaultValue);
    }

    /**
     * Same as getProperty(key), but throw an exception if the key doesn't exist.
     */
    public static String requireProperty(String key) {
        String value = getInstance().getProperty(key);
        if (value == null) {
            throw new IllegalStateException(String.format("The `%s` setting is not set", key));
        }
        return value;
    }

    public static void setProperty(String key, String value) {
        getInstance().setProperty(key, value);
    }

    public static void reset() {
        instance = null;
    }
}
