#!/bin/sh

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
                break
                ;;
            broker-server)
                PROJECTS_ARG="--projects code/common,code/core,code/broker-server"
                break
                ;;
            authorizer)
                PROJECTS_ARG="--projects code/core,code/authorizer"
                break
                ;;
            connector)
                PROJECTS_ARG="--projects code/common,code/connector"
                break
                ;;
            cloud-datastore)
                PROJECTS_ARG="--projects code/core,code/extensions/database/cloud-datastore"
                break
                ;;
            jdbc)
                PROJECTS_ARG="--projects code/core,code/extensions/database/jdbc"
                break
                ;;
            cloud-kms)
                PROJECTS_ARG="--projects code/core,code/extensions/encryption/cloud-kms"
                break
                ;;
            redis)
                PROJECTS_ARG="--projects code/core,code/extensions/caching/redis"
                break
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

    if [[ -n "${SPECIFIC_TEST}" ]]; then
        MVN_VARS="${MVN_VARS} -DfailIfNoTests=false -Dtest=${SPECIFIC_TEST}"
    fi

    if [[ -n "${GSUITE_DOMAIN}" ]]; then
        MVN_VARS="${MVN_VARS} -Dgsuite-domain=${GSUITE_DOMAIN}"
    fi

    set_projects_arg
    validate_project_var

    ENV_VARS="--env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json"
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


# Route to the requested action
case "$1" in
    ssh)
        shift
        ssh_function
        break
        ;;
    mvn)
        shift
        mvn $@
        break
        ;;
    build)
        shift
        build_packages $@
        break
        ;;
    clean)
        shift
        clean
        break
        ;;
    test)
        shift
        run_tests $@
        break
        ;;
    init_dev)
        shift
        init_dev
        break
        ;;
    restart_dev)
        shift
        restart_dev
        break
        ;;
    backup_artifacts)
        shift
        backup_artifacts $@
        break
        ;;
    dependency)
        shift
        dependency
        break
        ;;
    *)
        echo "Error: Unsupported command: '$1'" >&2
        exit 1
        ;;
esac