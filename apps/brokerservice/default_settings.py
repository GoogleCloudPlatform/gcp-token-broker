# Copyright 2019 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# GCP settings
GCP_PROJECT = None
GCP_REGION = None

# Number of concurrent grpc server threads
NUM_SERVER_THREADS = 10

# KMS settings
ENCRYPTION_CRYPTO_KEY_RING = 'broker-key-ring'
ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY = 'refresh-token-key'
ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY = 'access-token-cache-key'
ENCRYPTION_DELEGATION_TOKEN_CRYPTO_KEY = 'delegation-token-key'

# Host and port to serve the broker app from
SERVER_HOST = '0.0.0.0'
SERVER_PORT = 5000

# Origin's Kerberos realm
ORIGIN_REALM = None

# Domain name
DOMAIN_NAME = None

# Path to the broker principal's keytab
KEYTAB_PATH = '/secrets/broker.keytab'

# Path to the broker Oauth client ID for user login
CLIENT_SECRET_PATH = '/secrets/client_secret.json'

# Path to the TLS private key
TLS_KEY_PATH = '/secrets/tls.key'

# Path to the TLS certificate
TLS_CRT_PATH = '/secrets/tls.crt'

# Comma-separated whitelist of super users for impersonation
PROXY_USER_WHITELIST = ''

# Comma-separated whitelist API scopes
SCOPE_WHITELIST = 'https://www.googleapis.com/auth/devstorage.read_write'

# Broker principal's service name
BROKER_SERVICE_NAME = 'broker'

# Broker principal's host
BROKER_SERVICE_HOSTNAME = ''

# Project containing the shadow service account
SHADOW_PROJECT = ''

# Life duration for JWT tokens (the shorter the better)
JWT_LIFE = 30  # in seconds

# Session maximum lifetime (in milliseconds)
SESSION_MAXIMUM_LIFETIME = 7 * 24 * 3600 * 1000  # 7 days

# Session lifetime increment (in milliseconds)
SESSION_RENEW_PERIOD = 24 * 3600 * 1000  # 24 hours

# Remote cache lifetime for access tokens
ACCESS_TOKEN_REMOTE_CACHE_TIME = 60  # in seconds

# Local cache lifetime for access tokens
ACCESS_TOKEN_LOCAL_CACHE_TIME = 30  # in seconds

# Redis cache backend settings
REDIS_CACHE_HOST = 'localhost'
REDIS_CACHE_PORT = 6379
REDIS_CACHE_DB = 0

# Redis database backend settings
REDIS_DATABASE_HOST = 'localhost'
REDIS_DATABASE_PORT = 6379
REDIS_DATABASE_DEFAULT_DB_NUMBER = 1

# Access token provider backend
PROVIDER_BACKEND = 'broker.providers.RefreshTokenProvider'

# Authentication backend class
AUTH_BACKEND = 'broker.authentication.KerberosAuthBackend'

# Cache backend class
CACHE_BACKEND = 'broker.caching.RedisCacheBackend'

# Database backend class
DATABASE_BACKEND = 'common.database.CloudDatastoreDatabaseBackend'

# Logging backend class
LOGGING_BACKEND = 'broker.logging.StructLogLoggingBackend'

# Namespace for logging
LOGGING_NAMESPACE = 'broker'

# Base level for logging
LOGGING_LEVEL = 'INFO'