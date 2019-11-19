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


# Creates a directory and copies some files to it.
# The copied files are keytabs and secrets created for the
# demo environment (See: https://github.com/GoogleCloudPlatform/gcp-token-broker/blob/master/docs/deploy/index.md).
function backup_artifacts() {
    if [ $# -ne 1 ]; then
        echo "Please provide a directory name"
        exit 1
    fi
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
    docker exec -it broker-dev bash -c "mvn package -DskipTests ${PROJECTS_ARG}"
}


# Sets the proper "--projects" argument for Maven
# based on the given module name.
function set_projects_arg() {
    if [[ -n "${MODULE}" ]]; then
        case "${MODULE}" in
            core)
                PROJECTS_ARG="--projects apps/core"
                break
                ;;
            broker)
                PROJECTS_ARG="--projects apps/common,apps/core,apps/broker"
                break
                ;;
            authorizer)
                PROJECTS_ARG="--projects apps/core,apps/authorizer"
                break
                ;;
            connector)
                PROJECTS_ARG="--projects apps/common,connector"
                break
                ;;
            cloud-datastore)
                PROJECTS_ARG="--projects apps/core,apps/extensions/database/cloud-datastore"
                break
                ;;
            jdbc)
                PROJECTS_ARG="--projects apps/core,apps/extensions/database/jdbc"
                break
                ;;
            cloud-kms)
                PROJECTS_ARG="--projects apps/core,apps/extensions/encryption/cloud-kms"
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
                TEST=$2
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

    if [[ -n "${TEST}" ]]; then
        MVN_VARS="-DfailIfNoTests=false -Dtest=${TEST}"
    fi

    set_projects_arg
    validate_project_var

    GCP_OPTIONS="--env APP_SETTING_GCP_PROJECT=${PROJECT} --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json"
    set -x
    docker exec -it ${GCP_OPTIONS} broker-dev bash -c "mvn test ${PROJECTS_ARG} ${MVN_VARS}"
}


# Starts a development container
function start_dev() {
    set -x
	docker run -it -v $PWD:/base -w /base -p 7070:7070 --detach --name broker-dev ubuntu:18.04 && \
	${docker_cmd} "apps/broker/install-dev.sh"
}


# Route to the requested action
case "$1" in
    build)
        shift
        build_packages $@
        break
        ;;
    test)
        shift
        run_tests $@
        break
        ;;
    start_dev)
        shift
        start_dev
        break
        ;;
    backup_artifacts)
        shift
        backup_artifacts $@
        break
        ;;
esac