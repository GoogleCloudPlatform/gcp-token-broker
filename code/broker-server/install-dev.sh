#!/usr/bin/env bash

# Copyright 2020 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -xeuo pipefail

source "/base/code/broker-server/install.sh"

# Maven and its dependencies
apt-get install -y libatomic1 maven

# Create a script to restart services
touch /restart-services.sh
chmod u+x /restart-services.sh

# PostgreSQL
echo exit 0 > /usr/sbin/policy-rc.d
DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql
echo "CREATE ROLE testuser LOGIN ENCRYPTED PASSWORD 'UNSECURE-PASSWORD';" | su postgres -c "psql"
su postgres -c "createdb broker --owner testuser"
echo "service postgresql restart" >> /restart-services.sh
# Make the Postgres server available outside the container
sed -Ei "s/#listen_addresses\s+=\s+'localhost'/listen_addresses = '*'/g" /etc/postgresql/*/main/postgresql.conf
echo "host    all    all    0.0.0.0/0    md5" >> /etc/postgresql/14/main/pg_hba.conf

# MariaDB
apt install -y mariadb-server
echo "CREATE DATABASE broker DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;" | mariadb
echo "CREATE USER 'testuser' IDENTIFIED BY 'UNSECURE-PASSWORD';" | mariadb
echo "GRANT ALL privileges ON *.* TO 'testuser'@'%';" | mariadb
echo "service mariadb restart" >> /restart-services.sh
# Make the MariaDB server available outside the container
sed -Ei 's/bind-address\s+=\s+127.0.0.1/bind-address=0.0.0.0/g' /etc/mysql/mariadb.conf.d/50-server.cnf

# Redis
apt-get install -y redis-server
# Apparently need to run 'shutdown' as 'service stop' and 'service restart' don't seem to kill the process
echo "redis-cli shutdown; service redis-server start" >> /restart-services.sh
# Make the Redis server available outside the container
sed 's/^bind 127.0.0.1 ::1/bind 0.0.0.0/' -i /etc/redis/redis.conf
sed 's/^protected-mode yes/protected-mode no/' -i /etc/redis/redis.conf

# Node.JS tools
apt install -y npm
npm install --global remark-cli remark-validate-links  # Used for code linting (`./run.sh lint`)
export NODE_PATH="/usr/local/lib/node_modules"

# (Re)start all services
/restart-services.sh