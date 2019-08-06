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
redis-server &

# Kerberos --------------------------------------------

apt-get update

export REALM=EXAMPLE.COM
export BROKER_HOST=testhost

DEBIAN_FRONTEND=noninteractive apt-get -yq install krb5-kdc krb5-admin-server

cat << EOF > /etc/krb5.conf
[realms]
    ${REALM} = {
        kdc = localhost:88
        admin_server = localhost:749
    }
[libdefaults]
    default_realm = ${REALM}
    dns_lookup_realm = false
    dns_lookup_kdc = false
EOF


cat << EOF > /etc/krb5kdc/kdc.conf
[kdcdefaults]
    kdc_ports = 750,88

[realms]
    ${REALM} = {
        database_name = /etc/krb5kdc/principal
        admin_keytab = FILE:/etc/krb5kdc/kadm5.keytab
        acl_file = /etc/krb5kdc/kadm5.acl
        key_stash_file = /etc/krb5kdc/.k5.${REALM}
        kdc_ports = 750,88
        max_life = 10h 0m 0s
        max_renewable_life = 7d 0h 0m 0s
        master_key_type = des3-hmac-sha1
        supported_enctypes = aes256-cts:normal arcfour-hmac:normal des3-hmac-sha1:normal des-cbc-crc:normal des:normal des:v4 des:norealm des:onlyrealm des:afs3
        default_principal_flags = +preauth
    }
EOF


cat << EOF >> /etc/krb5kdc/kadm5.acl
root *
EOF

mkdir /var/log/kerberos
touch /var/log/kerberos/krb5libs.log
touch /var/log/kerberos/krb5kdc.log
touch /var/log/kerberos/kadmind.log

KDC_DB_KEY=$(openssl rand -base64 32)
/usr/sbin/kdb5_util create -s -W -P "${KDC_DB_KEY}"

service krb5-kdc restart
service krb5-admin-server restart

kadmin.local -q "addprinc -randkey root"

# Create directories to host the keytabs
mkdir -p /etc/security/keytabs/broker
mkdir -p /etc/security/keytabs/users

# Create broker principal and keytab
kadmin.local -q "addprinc -randkey broker/${BROKER_HOST}"
kadmin.local -q "ktadd -k /etc/security/keytabs/broker/broker.keytab broker/${BROKER_HOST}"

# Create user principals and keytab
kadmin.local -q "addprinc -randkey alice"
kadmin.local -q "ktadd -k /etc/security/keytabs/users/alice.keytab alice"
kadmin.local -q "addprinc -randkey bob"
kadmin.local -q "ktadd -k /etc/security/keytabs/users/bob.keytab bob"