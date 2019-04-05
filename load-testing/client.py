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
from base64 import b64encode, b64decode
import time

import grpc
import gssapi
from locust import Locust, TaskSet, events, task

from brokerservice.protobuf import broker_pb2
from brokerservice.protobuf.broker_pb2_grpc import BrokerStub

BROKER_PORT = 443
BROKER_USER = 'broker'
BROKER_REALM = 'BROKER'

# Retrieve the TLS certificate from the VM metadata
out = subprocess.Popen(
    ['/usr/share/google/get_metadata_value', 'attributes/gcp-token-broker-tls-certificate'],
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT)
certificate, _ = out.communicate()


class BrokerClient:

    def __init__(self, host, credentials):
        self.host = host
        self.credentials = credentials

    def get_metadata(self):
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
        metadata = [('authorization', 'Negotiate {}'.format(spnego_token))]
        return metadata

    def get_stub(self):
        credentials = grpc.ssl_channel_credentials(certificate)
        channel = grpc.secure_channel('{}:{}'.format(self.host, BROKER_PORT), credentials)
        return BrokerStub(channel)

    def call_endpoint(self, endpoint, parameters=None):
        start_time = time.time()
        try:
            stub = self.get_stub()
            metadata = self.get_metadata()

            # Get the request object
            request_name = '{}Request'.format(endpoint)
            request = getattr(broker_pb2, request_name)()

            # Set given parameters to the request
            if parameters is not None:
                for key, value in parameters.items():
                    setattr(request, key, value)

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
