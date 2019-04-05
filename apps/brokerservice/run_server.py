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

import sys
sys.path.append('..')  # Make 'common' package discoverable

import os
from concurrent import futures
import time

import grpc

from common.conf import settings
from brokerservice.protobuf.broker_pb2_grpc import add_BrokerServicer_to_server
from broker.endpoints import BrokerServicer


def run_server():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=int(settings.NUM_SERVER_THREADS)))
    add_BrokerServicer_to_server(BrokerServicer(), server)

    # Load TLS certificate and key
    with open(settings.TLS_KEY_PATH, 'rb') as f:
        private_key = f.read()
    with open(settings.TLS_CRT_PATH, 'rb') as f:
        certificate_chain = f.read()
    server_credentials = grpc.ssl_server_credentials( ( (private_key, certificate_chain), ) )

    address = f'{settings.SERVER_HOST}:{settings.SERVER_PORT}'
    print(f'Server listening on {address}...')
    server.add_secure_port(address, server_credentials)
    server.start()

    try:
        while True:
            time.sleep(86400)
    except KeyboardInterrupt:
        server.stop(0)

if __name__ == '__main__':
    run_server()