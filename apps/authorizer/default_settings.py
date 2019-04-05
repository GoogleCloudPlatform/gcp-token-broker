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

# KMS settings
ENCRYPTION_CRYPTO_KEY_RING = 'broker-key-ring'
ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY = 'refresh-token-key'

# Domain name
DOMAIN_NAME = None

# Path to the broker Oauth client ID for user login
CLIENT_SECRET_PATH = '/secrets/client_secret.json'

# Path to the Flask secret key
FLASK_SECRET_PATH = '/secrets/authorizer-flask-secret.key'

# Database backend class
DATABASE_BACKEND = 'common.database.CloudDatastoreDatabaseBackend'

# Comma-separated whitelist API scopes
SCOPE_WHITELIST = 'https://www.googleapis.com/auth/devstorage.read_write'