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

from google.cloud import kms_v1

from conf import settings


def encrypt(key_id, plaintext):
    """
    Encrypt the given plain text value with the given Cloud KMS key.
    """
    client = kms_v1.KeyManagementServiceClient()
    key = client.crypto_key_path_path(
        settings.GCP_PROJECT,
        settings.ENCRYPTION_CRYPTO_KEY_RING_REGION,
        settings.ENCRYPTION_CRYPTO_KEY_RING,
        key_id)
    response = client.encrypt(key, plaintext.encode('utf-8'))
    return response.ciphertext


def decrypt(key_id, ciphertext):
    """
    Decrypt the given encrypted value with the given Cloud KMS key.
    """
    client = kms_v1.KeyManagementServiceClient()
    key = client.crypto_key_path_path(
        settings.GCP_PROJECT,
        settings.ENCRYPTION_CRYPTO_KEY_RING_REGION,
        settings.ENCRYPTION_CRYPTO_KEY_RING,
        key_id)
    response = client.decrypt(key, ciphertext)
    return response.plaintext.decode('utf-8')