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

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

import com.google.cloud.broker.utils.EnvUtils;

import java.util.HashMap;
import java.util.Map;

public class AppSettings {

    public final static String AUTHORIZER_HOST = "AUTHORIZER_HOST";
    public final static String AUTHORIZER_PORT = "AUTHORIZER_PORT";
    public final static String LOGGING_LEVEL = "LOGGING_LEVEL";
    public final static String SERVER_HOST = "SERVER_HOST";
    public final static String SERVER_PORT = "SERVER_PORT";
    public final static String TLS_ENABLED = "TLS_ENABLED";
    public final static String TLS_CRT_PATH = "TLS_CRT_PATH";
    public final static String TLS_KEY_PATH = "TLS_KEY_PATH";
    public final static String SESSION_LOCAL_CACHE_TIME = "SESSION_LOCAL_CACHE_TIME";
    public final static String PROVIDER = "PROVIDER";
    public final static String SESSION_MAXIMUM_LIFETIME = "SESSION_MAXIMUM_LIFETIME";
    public final static String SESSION_RENEW_PERIOD = "SESSION_RENEW_PERIOD";
    public final static String PROXY_USER_WHITELIST = "PROXY_USER_WHITELIST";
    public final static String SCOPE_WHITELIST = "SCOPE_WHITELIST";
    public final static String ACCESS_TOKEN_LOCAL_CACHE_TIME = "ACCESS_TOKEN_LOCAL_CACHE_TIME";
    public final static String ACCESS_TOKEN_REMOTE_CACHE_TIME = "ACCESS_TOKEN_REMOTE_CACHE_TIME";
    public final static String REMOTE_CACHE = "REMOTE_CACHE";
    public final static String JWT_LIFE = "JWT_LIFE";
    public final static String SHADOW_PROJECT = "SHADOW_PROJECT";
    public final static String SHADOW_USERNAME_PATTERN = "SHADOW_USERNAME_PATTERN";
    public final static String JSON_FILE_CREDENTIALS_PROVIDER_BASE_DIR = "JSON_FILE_CREDENTIALS_PROVIDER_BASE_DIR";
    public final static String DATABASE_BACKEND = "DATABASE_BACKEND";
    public final static String ENCRYPTION_BACKEND = "ENCRYPTION_BACKEND";
    public final static String REDIS_CACHE_HOST = "REDIS_CACHE_HOST";
    public final static String REDIS_CACHE_PORT = "REDIS_CACHE_PORT";
    public final static String REDIS_CACHE_DB = "REDIS_CACHE_DB";
    public final static String AUTHENTICATION_BACKEND = "AUTHENTICATION_BACKEND";
    public final static String OAUTH_CLIENT_SECRET_JSON_PATH = "OAUTH_CLIENT_SECRET_JSON_PATH";
    public final static String OAUTH_CLIENT_ID = "OAUTH_CLIENT_ID";
    public final static String OAUTH_CLIENT_SECRET = "OAUTH_CLIENT_SECRET";
    public final static String KEYTABS = "KEYTABS";
    public final static String DOMAIN_NAME = "DOMAIN_NAME";
    public final static String GCP_PROJECT = "GCP_PROJECT";
    public final static String DATABASE_JDBC_URL = "DATABASE_JDBC_URL";
    public final static String ENCRYPTION_DEK_URI = "ENCRYPTION_DEK_URI";
    public final static String ENCRYPTION_KEK_URI = "ENCRYPTION_KEK_URI";

    private static Config instance;
    static {
        reset(); // Initialize instance
    }

    public AppSettings() {}

    private static Map<String, String> loadEnvironmentSettings() {
        // Override default settings with potential environment variables
        Map<String, String> env = EnvUtils.getenv();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().startsWith("APP_SETTING_")) {
                result.put(entry.getKey().substring("APP_SETTING_".length()), entry.getValue());
            }
        }
        return result;
    }

    public static Config getInstance() {
        return instance;
    }

    static void setInstance(Config newInstance) {
        instance = newInstance;
    }

    static void reset() {
        setInstance(ConfigFactory.parseMap(loadEnvironmentSettings()).withFallback(ConfigFactory.load()));
    }
}