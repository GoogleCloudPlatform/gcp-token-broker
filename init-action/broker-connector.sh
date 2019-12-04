#!/bin/bash

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

# In general we want to enable debug through -x
# But there are also some commands involving passwords/keys
# so make sure you turn it off (set +x) before such commands.
set -xeuo pipefail

#####################################################################
# WARNING: !! DO NOT USE IN PRODUCTION !!
# This script is an initialization action for Cloud Dataproc that
# installs dependencies to interact with the GCP token broker.
# This script is provided only as a reference and should *not* be
# used as-is in production.
#####################################################################

GCS_CONN_VERSION="hadoop2-2.0.0-RC2"
ROLE="$(/usr/share/google/get_metadata_value attributes/dataproc-role)"
WORKER_COUNT="$(/usr/share/google/get_metadata_value attributes/dataproc-worker-count)"
HADOOP_CONF_DIR="/etc/hadoop/conf"
HADOOP_LIB_DIR="/usr/lib/hadoop/lib"
DATAPROC_LIB_DIR="/usr/local/share/google/dataproc/lib"
DATAPROC_REALM=$(sudo cat /etc/krb5.conf | grep "default_realm" | awk '{print $NF}')

# Flag checking whether init actions will run early.
# This will affect whether nodemanager should be restarted
readonly early_init="$(/usr/share/google/get_metadata_value attributes/dataproc-option-run-init-actions-early || echo 'false')"

readonly broker_version="$(/usr/share/google/get_metadata_value attributes/broker-version)"
readonly broker_uri="$(/usr/share/google/get_metadata_value attributes/gcp-token-broker-uri)"
readonly broker_kerberos_principal="$(/usr/share/google/get_metadata_value attributes/gcp-token-broker-kerberos-principal)"
readonly broker_connector_jar="$(/usr/share/google/get_metadata_value attributes/broker-connector-jar)"
readonly origin_realm="$(/usr/share/google/get_metadata_value attributes/origin-realm)"
readonly test_users="$(/usr/share/google/get_metadata_value attributes/test-users)"
set +x
readonly broker_tls_certificate="$(/usr/share/google/get_metadata_value attributes/gcp-token-broker-tls-certificate)"
set -x

function set_property_in_xml() {
  bdconfig set_property \
    --configuration_file $1 \
    --name "$2" --value "$3" \
    --create_if_absent \
    --clobber \
    || err "Unable to set $2"
}

function set_property_core_site() {
  set_property_in_xml "${HADOOP_CONF_DIR}/core-site.xml" "$1" "$2"
}

function restart_master_services() {
  services=('hadoop-hdfs-namenode' 'hadoop-hdfs-secondarynamenode' 'hadoop-yarn-resourcemanager' 'hive-server2' 'hive-metastore' 'hadoop-yarn-timelineserver' 'hadoop-mapreduce-historyserver' 'spark-history-server')
  for service in "${services[@]}"; do
    if ( systemctl is-enabled --quiet "${service}" ); then
      systemctl restart "${service}" || err "Cannot restart service: ${service}"
    fi
  done
}

function restart_worker_services() {
  systemctl restart hadoop-hdfs-datanode || err 'Cannot restart datanode'
  if [[ "${early_init}" == 'false' ]]; then
    systemctl restart hadoop-yarn-nodemanager || err 'Cannot restart node manager'
  fi
}

# Set some hadoop config properties
set_property_core_site "fs.gs.system.bucket" ""
set_property_core_site "fs.gs.delegation.token.binding" "com.google.cloud.broker.hadoop.fs.BrokerDelegationTokenBinding"
set_property_core_site "gcp.token.broker.uri" "$broker_uri"
set_property_core_site "gcp.token.broker.kerberos.principal" "$broker_kerberos_principal"
set +x
set_property_core_site "gcp.token.broker.tls.certificate" "$broker_tls_certificate"
set -x

# Get connector's lib directory
if [[ -d ${DATAPROC_LIB_DIR} ]]; then
    # For Dataproc 1.4
    lib_dir=${DATAPROC_LIB_DIR}
else
    # For Dataproc < 1.4
    lib_dir=${HADOOP_LIB_DIR}
fi

# Remove the old GCS connector
cd ${lib_dir}
rm -f "gcs-connector-"*

# Download the JARs
if [[ -n "${broker_connector_jar}" ]]; then
  gsutil cp ${broker_connector_jar} .
else
  wget https://repo1.maven.org/maven2/com/google/cloud/broker/broker-connector/hadoop2-${broker_version}/broker-connector-hadoop2-${broker_version}.jar
fi
wget https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/${GCS_CONN_VERSION}/gcs-connector-${GCS_CONN_VERSION}-shaded.jar

# Update version-less connector link if present
if [[ -L ${lib_dir}/gcs-connector.jar ]]; then
    ln -s -f "${lib_dir}/gcs-connector-${GCS_CONN_VERSION}-shaded.jar" "${lib_dir}/gcs-connector.jar"
fi

# Setup some useful env vars
PROJECT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/project/project-id" -H "Metadata-Flavor: Google")
ZONE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/zone" -H "Metadata-Flavor: Google" | awk -F/ '{print $NF}')
cat > /etc/profile.d/extra_env_vars.sh << EOL
export PROJECT=$PROJECT
export ZONE=$ZONE
export DATAPROC_REALM=$DATAPROC_REALM
export REALM=$origin_realm
EOL

# Create POSIX users (which need to exist on all nodes for Yarn to work)
USERS=${test_users}
if [[ -z "${USERS}" ]] ; then
  USERS="alice;bob;john"
fi
for i in $(echo $USERS | sed "s/;/ /g")
do
  adduser --disabled-password --gecos "" $i
done

# Create broker principal and keytab
if [[ "${ROLE}" == 'Master' ]]; then
  kadmin.local -q "addprinc -randkey broker/${broker_uri_hostname}"
  kadmin.local -q "ktadd -k /etc/security/keytab/broker.keytab broker/${broker_uri_hostname}"
fi

# Restart services ---------------------------------------------------------------
if [[ "${ROLE}" == 'Master' ]]; then
  # In single node mode, we run worker services on the master.
  if [[ "${WORKER_COUNT}" == '0' ]]; then
    restart_worker_services
  fi
  restart_master_services
fi

if [[ "${ROLE}" == 'Worker' ]]; then
  restart_worker_services
fi


# Restarts Dataproc Agent after successful initialization
# WARNING: this function relies on undocumented and not officially supported Dataproc Agent
# "sentinel" files to determine successful Agent initialization and not guaranteed
# to work in the future. Use at your own risk!
restart_dataproc_agent() {
  # Because Dataproc Agent should be restarted after initialization, we need to wait until
  # it will create a sentinel file that signals initialization competition (success or failure)
  while [[ ! -f /var/lib/google/dataproc/has_run_before ]]; do
    sleep 1
  done
  # If Dataproc Agent didn't create a sentinel file that signals initialization
  # failure then it means that initialization succeded and it should be restarted
  if [[ ! -f /var/lib/google/dataproc/has_failed_before ]]; then
    pkill -f com.google.cloud.hadoop.services.agent.AgentMain
  fi
}
export -f restart_dataproc_agent

# Schedule asynchronous Dataproc Agent restart so it will use updated connectors.
# It could not be restarted sycnhronously because Dataproc Agent should be restarted
# after its initialization, including init actions execution, has been completed.
bash -c restart_dataproc_agent & disown