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

apt-get update

DEBIAN_FRONTEND=noninteractive apt-get -yq install krb5-kdc krb5-admin-server

cat << EOF > /etc/krb5.conf
[realms]
    ${realm} = {
        kdc = localhost:88
        admin_server = localhost:749
    }
[libdefaults]
    default_realm = ${realm}
    dns_lookup_realm = false
    dns_lookup_kdc = false
EOF


cat << EOF > /etc/krb5kdc/kdc.conf
[kdcdefaults]
    kdc_ports = 750,88

[realms]
    ${realm} = {
        database_name = /etc/krb5kdc/principal
        admin_keytab = FILE:/etc/krb5kdc/kadm5.keytab
        acl_file = /etc/krb5kdc/kadm5.acl
        key_stash_file = /etc/krb5kdc/.k5.${realm}
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
/usr/sbin/kdb5_util create -s -W -P "$${KDC_DB_KEY}"

systemctl enable krb5-kdc; systemctl restart krb5-kdc
systemctl enable krb5-admin-server; systemctl restart krb5-admin-server

kadmin.local -q "addprinc -randkey root"

${extra_commands}

echo "
export REALM=${realm}
export PROJECT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/project/project-id" -H "Metadata-Flavor: Google")
export ZONE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/zone" -H "Metadata-Flavor: Google" | awk -F/ '{print $NF}')
" > /etc/profile.d/extra_env_vars.sh