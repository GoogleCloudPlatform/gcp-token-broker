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

import subprocess

import gssapi
from locust import Locust, TaskSequence, task, seq_task

from client import BrokerClient
from settings import TEST_USERS, REALM

SCOPE = 'https://www.googleapis.com/auth/devstorage.read_write'
BROKER_HOST = '10.2.1.255.nip.io'
USER = TEST_USERS[0]
USER_FULL = '{}@{}'.format(USER, REALM)


def get_certificate():
    # Retrieve the TLS certificate from the VM metadata
    out = subprocess.Popen(
        ['/usr/share/google/get_metadata_value', 'attributes/gcp-token-broker-tls-certificate'],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT)
    certificate, _ = out.communicate()
    return certificate


class JobSimulation(TaskSequence):

    def on_start(self):
        self.client.kinit(USER_FULL, USER)

    def on_stop(self):
        self.client.kdestroy()

    @seq_task(1)
    def get_session_token(self):
        response = self.client.call_endpoint(
            'GetSessionToken',
            dict(owner=USER_FULL, scope=SCOPE, renewer=USER_FULL)
        )
        self.session_token = response.session_token

    @seq_task(2)
    def renew_session_token(self):
        self.client.call_endpoint(
            'RenewSessionToken',
            dict(session_token=self.session_token),
        )

    @seq_task(3)
    @task(100)
    def get_access_token(self):
        self.client.call_endpoint(
            'GetAccessToken',
            dict(owner=USER_FULL, scope=SCOPE),
            self.session_token
        )

    @seq_task(4)
    def cancel_session_token(self):
        self.client.call_endpoint(
            'CancelSessionToken',
            dict(session_token=self.session_token),
        )


class BrokerUser(Locust):
    host = BROKER_HOST
    task_set = JobSimulation
    min_wait = 0
    max_wait = 1

    def __init__(self, *args, **kwargs):
        self.client = BrokerClient(self.host, get_certificate())