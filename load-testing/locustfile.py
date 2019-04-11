# Copyright Google Inc. 2019
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import time
import tempfile
import subprocess

import gssapi
from locust import Locust, TaskSet, events, task

from client import BrokerClient
from settings import TEST_USERS, REALM

SCOPE = 'https://www.googleapis.com/auth/devstorage.read_write'
BROKER_HOST = '10.2.1.255.xip.io'


# Create credentials cache in temporary directory
TMPDIR = tempfile.mkdtemp()
CCACHE = 'FILE:{}/ccache'.format(TMPDIR)
os.environ['KRB5CCNAME'] = CCACHE
STORE = {b'ccache': CCACHE.encode('UTF-8')}

def login(principal, password):
    # Acquire new credentials
    name = gssapi.Name(principal, gssapi.NameType.kerberos_principal)
    acquire_credentials = gssapi.raw.acquire_cred_with_password(name, password)
    credentials = acquire_credentials.creds
    # Store credentials in the cache
    gssapi.raw.store_cred_into(STORE, credentials, usage='initiate', overwrite=True)
    return credentials

def get_certificate():
    # Retrieve the TLS certificate from the VM metadata
    out = subprocess.Popen(
        ['/usr/share/google/get_metadata_value', 'attributes/gcp-token-broker-tls-certificate'],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT)
    certificate, _ = out.communicate()
    return certificate

# Acquire credentatials for a user
USER = TEST_USERS[0]
USER_FULL = '{}@{}'.format(USER, REALM)
CREDENTIALS = login(USER_FULL, USER.encode('ascii'))
CERTIFICATE = get_certificate()


class UserBehavior(TaskSet):

    @task(1)
    def get_session_token(self):
        self.client.call_endpoint(
            'GetSessionToken',
            dict(owner=USER_FULL, scope=SCOPE)
        )

    @task(20)
    def get_access_token(self):
        self.client.call_endpoint(
            'GetAccessToken',
            dict(owner=USER_FULL, scope=SCOPE)
        )


class BrokerUser(Locust):
    host = BROKER_HOST
    task_set = UserBehavior
    min_wait = 0
    max_wait = 1

    def __init__(self, *args, **kwargs):
        self.client = BrokerClient(self.host, CREDENTIALS, CERTIFICATE)