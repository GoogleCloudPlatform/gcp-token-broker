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

source "/base/apps/broker/install.sh"

# Maven and its dependencies
apt-get install -y libatomic1 maven

# PostgreSQL
echo exit 0 > /usr/sbin/policy-rc.d
DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql
echo "CREATE ROLE testuser LOGIN ENCRYPTED PASSWORD 'UNSECURE-PASSWORD';" | su postgres -c "psql"
su postgres -c "createdb broker --owner testuser"

# MariaDB
apt install -y mariadb-server
echo "CREATE DATABASE broker DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;" | mariadb
echo "CREATE USER 'testuser' IDENTIFIED BY 'UNSECURE-PASSWORD';" | mariadb
echo "GRANT ALL privileges ON *.* TO 'testuser'@'%';" | mariadb

# Redis
apt-get install -y redis-server
ps aux | grep [r]edis-server  &> /dev/null
if [ $? != 0 ]; then
    # Start Redis server if it's not already running
    redis-server &
fi