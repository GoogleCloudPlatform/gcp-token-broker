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

package com.google.cloud.broker.settings;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigParseOptions;
import com.google.cloud.storage.BlobId;

import com.google.cloud.broker.utils.CloudStorageUtils;

public class AppSettings {

    // Means of passing settings to the application
    public final static String CONFIG_FILE_PROPERTY = "config.file";
    public final static String CONFIG_FILE_ENV_VAR = "CONFIG_FILE";
    public final static String CONFIG_BASE64_ENV_VAR = "CONFIG_BASE64";
    public final static String CONFIG_GCS_ENV_VAR = "CONFIG_GCS";

    // Individual settings
    public final static String GCP_PROJECT = "gcp-project";
    public final static String GSUITE_ADMIN = "gsuite-admin";
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
    public final static String PROXY_USERS = "proxy-users";
    public final static String SCOPES_WHITELIST = "scopes.whitelist";
    public final static String PROVIDER_BACKEND = "provider.backend";
    public final static String ACCESS_TOKEN_BOUNDARY_PERMISSIONS = "provider.access-tokens.boundary-permissions";
    public final static String ACCESS_TOKEN_LOCAL_CACHE_TIME = "provider.access-tokens.local-cache-time";
    public final static String ACCESS_TOKEN_REMOTE_CACHE_TIME = "provider.access-tokens.remote-cache-time";
    public final static String HYBRID_USER_PROVIDER = "provider.hybrid.user-provider";
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
    public final static String USER_MAPPER = "user-mapping.mapper";
    public final static String USER_MAPPING_RULES = "user-mapping.rules";
    public final static String SECRET_MANAGER_DOWNLOADS = "secret-manager.downloads";
    public final static String SYSTEM_CHECK_ENABLED = "system-check-enabled";


    private static Config instance;
    static {
        loadSettings(); // Initialize instance
    }

    public static Config getInstance() {
        return instance;
    }

    public static void setInstance(Config newInstance) {
        instance = newInstance;
    }

    static void loadSettings() {
        ConfigFactory.invalidateCaches();

        int provided = 0;
        String fileProperty = System.getProperty(CONFIG_FILE_PROPERTY);
        if (fileProperty != null)
            provided += 1;
        String fileEnvVar = System.getenv(CONFIG_FILE_ENV_VAR);
        if (fileEnvVar != null)
            provided += 1;
        String base64EnvVar = System.getenv(CONFIG_BASE64_ENV_VAR);
        if (base64EnvVar != null)
            provided += 1;
        String gcsEnvVar = System.getenv(CONFIG_GCS_ENV_VAR);
        if (gcsEnvVar != null)
            provided += 1;

        if (provided == 0) {
            // Only load the default settings
            setInstance(ConfigFactory.defaultReference());
        }
        else if (provided > 1) {
            throw new IllegalStateException(String.format(
                "You must provide only one of the following: the `%s` property or `%s`, `%s`, or `%s` environment variables.",
                CONFIG_FILE_PROPERTY, CONFIG_FILE_ENV_VAR, CONFIG_GCS_ENV_VAR, CONFIG_BASE64_ENV_VAR
            ));
        } else {
            ConfigParseOptions overrideOptions = ConfigParseOptions.defaults().setAllowMissing(false);
            Config config;
            if (fileProperty != null) {
                // Load settings from a file on the filesystem
                config = ConfigFactory.parseFile(new File(fileProperty), overrideOptions);
            } else if (fileEnvVar != null) {
                // Load settings from a file on the filesystem
                config = ConfigFactory.parseFile(new File(fileEnvVar), overrideOptions);
            } else if (base64EnvVar != null) {
                // Load settings from a base64-encoded string
                config = ConfigFactory.parseString(new String(Base64.getDecoder().decode(base64EnvVar)));
            } else {
                // Load settings from a GCS object
                URI uri;
                try {
                    uri = new URI(gcsEnvVar);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                BlobId blobId = BlobId.of(uri.getAuthority(), uri.getPath().substring(1));
                String blobString = new String(CloudStorageUtils.getCloudStorageClient().readAllBytes(blobId));
                config = ConfigFactory.parseString(blobString);
            }
            setInstance(config.withFallback(ConfigFactory.defaultReference()));
        }
    }
}