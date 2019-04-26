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
from base64 import b64encode, b64decode
import time
import tempfile
import shutil

import grpc
import gssapi
from locust import Locust, TaskSet, events, task

from brokerservice.protobuf import broker_pb2
from brokerservice.protobuf.broker_pb2_grpc import BrokerStub

BROKER_PORT = 443
BROKER_USER = 'broker'
BROKER_REALM = 'BROKER'

class BrokerClient:

    def kinit(self, principal, password):
        # Create credentials cache in temporary directory
        self.cache_dir = tempfile.mkdtemp()
        ccache = 'FILE:{}/ccache'.format(self.cache_dir)
        os.environ['KRB5CCNAME'] = ccache
        store = {b'ccache': ccache.encode('UTF-8')}
        # Acquire new credentials
        name = gssapi.Name(principal, gssapi.NameType.kerberos_principal)
        acquire_credentials = gssapi.raw.acquire_cred_with_password(name, password.encode('ascii'))
        self.credentials = acquire_credentials.creds
        # Store credentials in the cache
        gssapi.raw.store_cred_into(store, self.credentials, usage='initiate', overwrite=True)

    def kdestroy(self):
        self.credentials = None
        shutil.rmtree(self.cache_dir)

    def __init__(self, host, certificate):
        self.host = host
        self.certificate = certificate
        self.credentials = None

    def get_spnego_token(self):
        """
        Obtain a SPNEGO token for the broker service and set the token to
        the 'authorization' metadata header.
        """
        service_name_string = '{}/{}@{}'.format(BROKER_USER, self.host, BROKER_REALM)
        service_name = gssapi.Name(service_name_string, gssapi.NameType.kerberos_principal)
        spnego_mech_oid = gssapi.raw.OID.from_int_seq('1.3.6.1.5.5.2')
        context = gssapi.SecurityContext(
            name=service_name, mech=spnego_mech_oid, usage='initiate', creds=self.credentials)
        response = context.step()
        spnego_token = b64encode(response).decode()
        return spnego_token

    def get_metadata(self, session_token=None):
        if session_token is not None:
            metadata = [('authorization', 'BrokerSession {}'.format(session_token))]
        else:
            metadata = [('authorization', 'Negotiate {}'.format(self.get_spnego_token()))]
        return metadata

    def get_stub(self):
        ssl_credentials = grpc.ssl_channel_credentials(self.certificate)
        channel = grpc.secure_channel('{}:{}'.format(self.host, BROKER_PORT), ssl_credentials)
        return BrokerStub(channel)

    def call_endpoint(self, endpoint, parameters=None, session_token=None):
        start_time = time.time()
        try:
            stub = self.get_stub()

            # Get the request object
            request_name = '{}Request'.format(endpoint)
            request = getattr(broker_pb2, request_name)()

            # Set given parameters to the request
            if parameters is not None:
                for key, value in parameters.items():
                    setattr(request, key, value)

            # Set SPNEGO token in the metadata
            metadata = self.get_metadata(session_token)

            # Call the gRPC endpoint
            endpoint_func = getattr(stub, endpoint)
            response = endpoint_func(request, metadata=metadata)
        except Exception as e:
            # Submit metrics to Locust in case of errors
            total_time = int((time.time() - start_time) * 1000)
            events.request_failure.fire(request_type='broker_rpc', name=endpoint, response_time=total_time, exception=e)
        else:
            # Submit metrics to Locust in case of success
            total_time = int((time.time() - start_time) * 1000)
            events.request_success.fire(request_type='broker_rpc', name=endpoint, response_time=total_time, response_length=0)
            return response
