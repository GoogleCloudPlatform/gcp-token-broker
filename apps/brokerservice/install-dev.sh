#!/usr/bin/env bash

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

source "/base/apps/brokerservice/install.sh"

# Install Redis
apt-get install -y redis-server
ps aux | grep [r]edis-server  &> /dev/null
if [ $? != 0 ]; then
    # Start Redis server if it's not already running
    redis-server &
fi

# Install Python dependencies for development/testing
pip3 install pytest==4.0.2 grpcio-tools==1.17.0 pycrypto==2.6.1