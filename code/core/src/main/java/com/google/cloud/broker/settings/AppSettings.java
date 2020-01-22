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

public class AppSettings {

    public final static String GCP_PROJECT = "gcp-project";
    public final static String GSUITE_DOMAIN = "gsuite-domain";
    public final static String AUTHORIZER_HOST = "authorizer.host";
    public final static String AUTHORIZER_PORT = "authorizer.port";
    public final static String LOGGING_LEVEL = "logging.level";
    public final static String SERVER_HOST = "server.host";
    public final static String SERVER_PORT = "server.port";
    public final static String TLS_ENABLED = "server.tls.enabled";
    public final static String TLS_CERTIFICATE_PATH = "server.tls.certificate-path";
    public final static String TLS_PRIVATE_KEY_PATH = "server.tls.private-key-path";
    public final static String SESSION_LOCAL_CACHE_TIME = "sessions.local-cache-time";
    public final static String SESSION_MAXIMUM_LIFETIME = "sessions.maximum-lifetime";
    public final static String SESSION_RENEW_PERIOD = "sessions.renew-period";
    public final static String PROXY_USER_WHITELIST = "proxy-users.whitelist";
    public final static String SCOPES_WHITELIST = "scopes.whitelist";
    public final static String PROVIDER_BACKEND = "provider.backend";
    public final static String ACCESS_TOKEN_LOCAL_CACHE_TIME = "provider.access-tokens.local-cache-time";
    public final static String ACCESS_TOKEN_REMOTE_CACHE_TIME = "provider.access-tokens.remote-cache-time";
    public final static String SHADOW_PROJECT = "provider.shadow-service-accounts.project";
    public final static String SHADOW_USERNAME_PATTERN = "provider.shadow-service-accounts.username-pattern";
    public final static String JSON_FILE_CREDENTIALS_PROVIDER_BASE_DIR = "provider.json-file-credentials.base-dir";
    public final static String DATABASE_BACKEND = "database.backend";
    public final static String DATABASE_JDBC_URL = "database.jdbc.driver-url";
    public final static String REMOTE_CACHE = "remote-cache.backend";
    public final static String REDIS_CACHE_HOST = "remote-cache.redis.host";
    public final static String REDIS_CACHE_PORT = "remote-cache.redis.port";
    public final static String REDIS_CACHE_DB = "remote-cache.redis.db";
    public final static String OAUTH_CLIENT_ID = "oauth.client-id";
    public final static String OAUTH_CLIENT_SECRET = "oauth.client-secret";
    public final static String OAUTH_CLIENT_SECRET_JSON_PATH = "oauth.client-secret-json-path";
    public final static String AUTHENTICATION_BACKEND = "authentication.backend";
    public final static String KEYTABS = "authentication.spnego.keytabs";
    public final static String ENCRYPTION_BACKEND = "encryption.backend";
    public final static String ENCRYPTION_DEK_URI = "encryption.cloud-kms.dek-uri";
    public final static String ENCRYPTION_KEK_URI = "encryption.cloud-kms.kek-uri";

    private static Config instance;
    static {
        reset(); // Initialize instance
    }

    public static Config getInstance() {
        return instance;
    }

    static void setInstance(Config newInstance) {
        instance = newInstance;
    }

    static void reset() {
        ConfigFactory.invalidateCaches();
        setInstance(ConfigFactory.load());
    }
}