#!/bin/bash

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

# ================================================================
# Utility script to run development tasks.
# This script assumes you have deployed the required resources
# in your test project (see: https://github.com/GoogleCloudPlatform/gcp-token-broker/blob/master/docs/contribute/regression-tests.md).
#
# Usage:
#   * Start the development container:
#
#     ./run.sh start_dev
#
#   * Build all Maven packages:
#
#     ./run.sh build
#
#   * Build Maven packages for a specific module, for example:
#
#     ./run.sh build -m authorizer
#
#   * Run the entire test suite:
#
#     ./run.sh test
#
#   * Run the tests for a specific module, for example:
#
#     ./run.sh test -m cloud-datastore
#
#   * Run a specific test class in a module, for example:
#
#     ./run.sh test -m broker -t ValidationTest
#
#   * Run a specific test method:
#
#     ./run.sh test -m broker -t ValidationTest#testValidateScope
#
# ================================================================

set -eo pipefail


MODULE=""
PROJECTS_ARG=""
CONTAINER="broker-dev"

# Creates a directory and copies some files to it.
# The copied files are keytabs and secrets created for the
# demo environment (See: https://github.com/GoogleCloudPlatform/gcp-token-broker/blob/master/docs/deploy/index.md).
function backup_artifacts() {
    if [ $# -ne 1 ]; then
        echo "Please provide a directory name"
        exit 1
    fi
    set -x
    mkdir -p backups/$1
    cp authorizer-tls.crt backups/$1
    cp authorizer-tls.csr backups/$1
    cp authorizer-tls.key backups/$1
    cp broker-tls.crt backups/$1
    cp broker-tls.csr backups/$1
    cp broker-tls.key backups/$1
    cp broker-tls.pem backups/$1
    cp broker.keytab backups/$1
    cp client_secret.json backups/$1
    cp kerberos-config.yaml backups/$1
    cp deploy/values_override.yaml backups/$1
    cp deploy/skaffold.yaml backups/$1
}


# Build the Maven packages
function build_packages() {
    while (( "$#" )); do
        case "$1" in
            -m|--module)
                MODULE=$2
                shift 2
                ;;
            -p|--project)
                PROJECT=$2
                shift 2
                ;;
            *)
                echo "Error: Unsupported argument: '$1'" >&2
                exit 1
                ;;
        esac
    done

    set_projects_arg
    validate_project_var

    set -x
    docker exec -it ${CONTAINER} bash -c "mvn clean package -DskipTests ${PROJECTS_ARG}"
}


# Sets the proper "--projects" argument for Maven
# based on the given module name.
function set_projects_arg() {
    if [[ -n "${MODULE}" ]]; then
        case "${MODULE}" in
            core)
                PROJECTS_ARG="--projects code/common,code/core"
                ;;
            broker-server)
                PROJECTS_ARG="--projects code/common,code/core,code/broker-server"
                ;;
            authorizer)
                PROJECTS_ARG="--projects code/core,code/authorizer"
                ;;
            connector)
                PROJECTS_ARG="--projects code/common,code/connector"
                ;;
            cloud-datastore)
                PROJECTS_ARG="--projects code/core,code/extensions/database/cloud-datastore"
                ;;
            jdbc)
                PROJECTS_ARG="--projects code/core,code/extensions/database/jdbc"
                ;;
            cloud-kms)
                PROJECTS_ARG="--projects code/core,code/extensions/encryption/cloud-kms"
                ;;
            redis)
                PROJECTS_ARG="--projects code/core,code/extensions/caching/redis"
                ;;
            *)
                echo "Invalid module: '${MODULE}'" >&2
                exit 1
        esac
    fi
}


# Makes sure that the PROJECT env var is set.
function validate_project_var() {
    if [[ -z "${PROJECT}" ]]; then
        echo "Please specify a project with the '-p' argument."
        exit 1
    fi
}


# Runs the regression tests
function run_tests() {
    while (( "$#" )); do
        case "$1" in
            -m|--module)
                MODULE=$2
                shift 2
                ;;
            -t|--test)
                SPECIFIC_TEST=$2
                shift 2
                ;;
            -p|--project)
                PROJECT=$2
                shift 2
                ;;
            -ga|--gsuite-admin)
                GSUITE_ADMIN=$2
                shift 2
                ;;
            -gd|--gsuite-domain)
                GSUITE_DOMAIN=$2
                shift 2
                ;;
            *)
                echo "Error: Unsupported argument: '$1'" >&2
                exit 1
                ;;
        esac
    done

    MVN_VARS="-Dgcp-project=${PROJECT}"
    ENV_VARS="--env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json"

    if [[ -n "${SPECIFIC_TEST}" ]]; then
        MVN_VARS="${MVN_VARS} -DfailIfNoTests=false -Dtest=${SPECIFIC_TEST}"
    fi

    if [[ -n "${GSUITE_ADMIN}" ]]; then
        MVN_VARS="${MVN_VARS} -Dgsuite-admin=${GSUITE_ADMIN}"
    fi

    if [[ -n "${GSUITE_DOMAIN}" ]]; then
        ENV_VARS="${ENV_VARS} --env GSUITE_DOMAIN=${GSUITE_DOMAIN}"
    fi

    set_projects_arg
    validate_project_var

    set -x
    docker exec -it ${ENV_VARS} ${CONTAINER} bash -c "mvn test ${PROJECTS_ARG} ${MVN_VARS}"
}

function mvn() {
    set -x
    ARGS="$@"
    docker exec -it ${CONTAINER} bash -c "mvn ${ARGS}"
}

function clean() {
    set -x
    docker exec -it ${CONTAINER} bash -c "mvn clean"
}

function update_version() {
    set -x
    docker exec -it ${CONTAINER} bash -c "mvn -Prelease versions:set -DgenerateBackupPoms=false -DnewVersion=$(cat VERSION)"
}

function dependency() {
    set -x
    docker exec -it ${CONTAINER} bash -c "mvn dependency:tree"
}

function ssh_function() {
    set -x
    docker exec -it ${CONTAINER} bash
}

# Initializes a development container
function init_dev() {
    set -x
	  docker run -it -v $PWD:/base -w /base -p 7070:7070 --detach --name ${CONTAINER} ubuntu:18.04 && \
	  docker exec -it ${CONTAINER} bash -c "code/broker-server/install-dev.sh"
}

# Restart the development container, in case the container was previously stopped.
function restart_dev {
    set -x
    docker start ${CONTAINER}
    docker exec -it ${CONTAINER} bash -c "/restart-services.sh"
}

# Upload connector jar to a Dataproc cluster
function upload_connector {
    LIB_DIR="/usr/local/share/google/dataproc/lib"
    VERSION="$(cat VERSION)"
    JAR="broker-connector-hadoop2-${VERSION}-jar-with-dependencies.jar"
    LOCAL_JAR=""
    SSH="gcloud compute ssh $1 --tunnel-through-iap"
    set -x
    # Upload new JAR
    gcloud compute scp code/connector/target/${JAR} $1:/tmp --tunnel-through-iap
    # Delete old JAR
    ${SSH} --command "sudo rm -f ${LIB_DIR}/broker-connector-*.jar"
    # Relocate new JAR
    ${SSH} --command "sudo mv /tmp/${JAR} ${LIB_DIR}"
    # Restart services
    ${SSH} --command "sudo systemctl restart hadoop-hdfs-namenode && sudo systemctl restart hadoop-hdfs-secondarynamenode && sudo systemctl restart hadoop-yarn-resourcemanager && sudo systemctl restart hive-server2 && sudo systemctl restart hive-metastore && sudo systemctl restart hadoop-yarn-timelineserver && sudo systemctl restart hadoop-mapreduce-historyserver && sudo systemctl restart spark-history-server"
}

function lint {
  # Find missing copyright notices
  echo "* Checking copyright notices..."
  NO_COPYRIGHT=$(git grep -L "Copyright" | grep -v "^docs/" | grep -v ".md$" | grep -v "^VERSION$") || true
  if [ -z "${NO_COPYRIGHT}" ]; then
    echo "✅ All files contain copyright notice."
  else
    echo -e "⛔️ Some file(s) do not include a copyright notice:\n\n${NO_COPYRIGHT}"
  fi

  # Lint documentation files
  echo "* Checking documentation..."
  docker exec -it ${CONTAINER} bash -c "NODE_PATH=/usr/local/lib/node_modules remark -u validate-links -u preset-lint-recommended /base/docs/"
}


# Route to the requested action
case "$1" in
    ssh)
        shift
        ssh_function
        ;;
    mvn)
        shift
        mvn $@
        ;;
    build)
        shift
        build_packages $@
        ;;
    clean)
        shift
        clean
        ;;
    test)
        shift
        run_tests $@
        ;;
    init_dev)
        shift
        init_dev
        ;;
    restart_dev)
        shift
        restart_dev
        ;;
    backup_artifacts)
        shift
        backup_artifacts $@
        ;;
    dependency)
        shift
        dependency
        ;;
    update_version)
        shift
        update_version
        ;;
    upload_connector)
        shift
        upload_connector $@
        ;;
    lint)
        shift
        lint
        ;;
    *)
        echo "Error: Unsupported command: '$1'" >&2
        exit 1
        ;;
esac