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

import java.util.Properties;
import java.util.Set;

public class BrokerSettings extends AppSettings {

    public BrokerSettings() {
        // GCP settings
        this.setProperty("GCP_PROJECT", "");
        this.setProperty("GCP_REGION", "");

        // Domain name
        this.setProperty("DOMAIN_NAME", "");

        // Host and port to serve the broker app from
        this.setProperty("SERVER_HOST", "0.0.0.0");
        this.setProperty("SERVER_PORT", "5000");

        // Path to the broker principals' keytabs
        this.setProperty("KEYTABS_PATH", "/keytabs");

        // Path to the broker Oauth client ID for user login
        this.setProperty("CLIENT_SECRET_PATH", "/secrets/client_secret.json");

        // Flag to enabled/disable TLS
        this.setProperty("TLS_ENABLED", "true");

        // Path to the TLS private key
        this.setProperty("TLS_KEY_PATH", "/secrets/tls.pem");

        // Path to the TLS certificate
        this.setProperty("TLS_CRT_PATH", "/secrets/tls.crt");

        // Broker principal's service name
        this.setProperty("BROKER_SERVICE_NAME", "broker");

        // Broker principal's host
        this.setProperty("BROKER_SERVICE_HOSTNAME", "");

        // Base level for logging
        this.setProperty("LOGGING_LEVEL", "INFO");

        // Comma-separated whitelist of proxy users for impersonation
        this.setProperty("PROXY_USER_WHITELIST", "");

        // Comma-separated whitelist API scopes
        this.setProperty("SCOPE_WHITELIST", "https://www.googleapis.com/auth/devstorage.read_write");

        // Cache backend
        this.setProperty("REMOTE_CACHE", "com.google.cloud.broker.caching.remote.RedisCache");

        // Remote cache lifetime for access tokens
        this.setProperty("ACCESS_TOKEN_REMOTE_CACHE_TIME", "60");  // in seconds

        // Local cache lifetime for access tokens
        this.setProperty("ACCESS_TOKEN_LOCAL_CACHE_TIME", "30");  // in seconds

        // Local cache lifetime for session details
        this.setProperty("SESSION_LOCAL_CACHE_TIME", "30");  // in seconds

        // Access token provider backend
        this.setProperty("PROVIDER", "com.google.cloud.broker.accesstokens.providers.RefreshTokenProvider");

        // Encryption backend
        this.setProperty("ENCRYPTION_BACKEND", "com.google.cloud.broker.encryption.backends.CloudKMSBackend");

        // Database backend class
        this.setProperty("DATABASE_BACKEND", "com.google.cloud.broker.database.backends.CloudDatastoreBackend");

        // Life duration for JWT tokens (the shorter the better)
        this.setProperty("JWT_LIFE", "30");  // in seconds

        // Project containing the shadow service account
        this.setProperty("SHADOW_PROJECT", "");

        // Session maximum lifetime (in milliseconds)
        this.setProperty("SESSION_MAXIMUM_LIFETIME", Integer.toString(7 * 24 * 3600 * 1000));  // 7 days

        // Session lifetime increment (in milliseconds)
        this.setProperty("SESSION_RENEW_PERIOD", Integer.toString(24 * 3600 * 1000));  // 24 hours

        // KMS settings
        this.setProperty("ENCRYPTION_CRYPTO_KEY_RING", "broker-key-ring");
        this.setProperty("ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY", "refresh-token-key");
        this.setProperty("ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY", "access-token-cache-key");
        this.setProperty("ENCRYPTION_DELEGATION_TOKEN_CRYPTO_KEY", "delegation-token-key");
    }

}
