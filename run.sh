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
#     ./run.sh init_dev
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
#     ./run.sh test -m db-datastore
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


function update_builds_dir() {
  set +x
  # Copy all the built files to a temporary directory.
  # This is useful to trigger a new deployment with skaffold in watch mode.
  temp_dir=$(mktemp -d)
  built_jar_dir="./code/built-jars"
  cp ./code/authorizer/target/authorizer-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/broker-server/target/broker-server-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/extensions/caching/cloud-datastore/target/cache-backend-cloud-datastore-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/extensions/caching/redis/target/cache-backend-redis-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/extensions/database/jdbc/target/database-backend-jdbc-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/extensions/database/cloud-datastore/target/database-backend-cloud-datastore-*-jar-with-dependencies.jar ${temp_dir}
  cp ./code/extensions/encryption/cloud-kms/target/encryption-backend-cloud-kms-*-jar-with-dependencies.jar ${temp_dir}
  rm -rf ${built_jar_dir}
  mv ${temp_dir} ${built_jar_dir}
  set -x
  echo "Copied built JARs to: ${built_jar_dir}"
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

    set -x
    docker exec -it ${CONTAINER} bash -c "mvn clean package -DskipTests ${PROJECTS_ARG}"
    update_builds_dir
}


# Sets the proper "--projects" argument for Maven
# based on the given module name.
function set_projects_arg() {
    if [[ -n "${MODULE}" ]]; then
        case "${MODULE}" in
            core)
                PROJECTS_ARG="--projects code/core"
                ;;
            broker-server)
                PROJECTS_ARG="--projects code/core,code/broker-server"
                ;;
            authorizer)
                PROJECTS_ARG="--projects code/core,code/authorizer"
                ;;
            connector)
                PROJECTS_ARG="--projects code/client/client-lib,code/client/hadoop-connector"
                ;;
            client)
                PROJECTS_ARG="--projects code/client/client-lib"
                ;;
            db-datastore)
                PROJECTS_ARG="--projects code/core,code/extensions/database/cloud-datastore"
                ;;
            jdbc)
                PROJECTS_ARG="--projects code/core,code/extensions/database/jdbc"
                ;;
            kms)
                PROJECTS_ARG="--projects code/core,code/extensions/encryption/cloud-kms"
                ;;
            cache-redis)
                PROJECTS_ARG="--projects code/core,code/extensions/caching/redis"
                ;;
            cache-datastore)
                PROJECTS_ARG="--projects code/core,code/extensions/caching/cloud-datastore"
                ;;
            *)
                echo "Invalid module: '${MODULE}'" >&2
                exit 1
        esac
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
                TEST_PROJECT=$2
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
            -d|--debug)
                DEBUG=1
                shift
                ;;
            *)
                echo "Error: Unsupported argument: '$1'" >&2
                exit 1
                ;;
        esac
    done

    if [[ -n "${TEST_PROJECT}" ]]; then
        PROPERTIES="-Dgcp-project=${TEST_PROJECT}"
    else
        PROPERTIES="-Dgcp-project=${PROJECT}"
    fi

    if [[ -n "${SPECIFIC_TEST}" ]]; then
        PROPERTIES="${PROPERTIES} -DfailIfNoTests=false -Dtest=${SPECIFIC_TEST}"
    fi

    if [[ -n "${GSUITE_ADMIN}" ]]; then
        PROPERTIES="${PROPERTIES} -Dgsuite-admin=${GSUITE_ADMIN}"
    fi

    if [[ -n "${GSUITE_DOMAIN}" ]]; then
        PROPERTIES="${PROPERTIES} -Dgsuite-domain=${GSUITE_DOMAIN}"
    fi

    if [[ -n "${DEBUG}" ]]; then
        PROPERTIES="${PROPERTIES} -Dmaven.surefire.debug"
    fi

    set_projects_arg

    set -x
    GOOGLE_APPLICATION_CREDENTIALS=$PWD/service-account-key.json sh -c "mvn test ${PROJECTS_ARG} ${PROPERTIES}"
}

function clean() {
    set -x
    docker exec -it ${CONTAINER} bash -c "mvn clean"
}

function compile_protobuf {
  set -x
  docker exec -it ${CONTAINER} bash -c "mvn protobuf:compile --projects code/broker-server,code/client/client-lib"
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
    # Ports:
    # 7070: Test coverage UI
    # 5005: Java remote debugging
    # 5432: PostgreSQL
    # 3306: MariaDB
    # 6379: Redis
	  docker run -it -v $PWD:/base -w /base -p 7070:7070 -p 5005:5005 -p 5432:5432 -p 3306:3306 -p 6379:6379 --detach --name ${CONTAINER} ubuntu:22.04 && \
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
    JAR="broker-hadoop-connector-hadoop2-${VERSION}-jar-with-dependencies.jar"
    SSH="gcloud compute ssh $1"
    set -x
    # Upload new JAR
    gcloud compute scp code/client/hadoop-connector/target/${JAR} $1:/tmp
    # Delete old JAR
    ${SSH} --command "sudo rm -f ${LIB_DIR}/broker-hadoop-connector-*.jar"
    # Relocate new JAR
    ${SSH} --command "sudo mv /tmp/${JAR} ${LIB_DIR}"
    # Restart services
    ${SSH} --command "sudo systemctl restart hadoop-hdfs-namenode && sudo systemctl restart hadoop-hdfs-secondarynamenode && sudo systemctl restart hadoop-yarn-resourcemanager && sudo systemctl restart hive-server2 && sudo systemctl restart hive-metastore && sudo systemctl restart hadoop-yarn-timelineserver && sudo systemctl restart hadoop-mapreduce-historyserver && sudo systemctl restart spark-history-server"
}

function lint {
  # Find missing copyright notices
  echo -e "*** Checking copyright notices:\n"
  NO_COPYRIGHT=$(git grep -L "Copyright" | grep -v "^docs/" | grep -v ".md$" | grep -v "^VERSION$") || true
  if [ -z "${NO_COPYRIGHT}" ]; then
    echo -e "✅ All files contain copyright notice.\n"
  else
    echo -e "🛑 Some file(s) do not include a copyright notice:\n\n${NO_COPYRIGHT}\n"
  fi

  # Lint documentation files
  echo -e "*** Checking documentation:\n"
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
    protobuf)
        shift
        compile_protobuf
        ;;
    *)
        echo "Error: Unsupported command: '$1'" >&2
        exit 1
        ;;
esac