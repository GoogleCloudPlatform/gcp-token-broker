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

# ----------------------------------------
# Utility script to run development tasks
# ----------------------------------------

function run_tests() {
    echo "YOYO: $#"
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
        --) # End of argument parsing
        shift
        break
        ;;
        *) # Unsupported arguments
        echo "Error: Unsupported argument $1" >&2
        exit 1
        ;;
    esac
    done

    if [[ -n "${TEST}" ]]; then
        MVN_VARS="-DfailIfNoTests=false -Dtest=${TEST}"
    fi

    case "${MODULE}" in
        core)
        PROJECTS="--projects apps/core"
        break
        ;;
        broker)
        PROJECTS="--projects apps/common,apps/core,apps/broker"
        break
        ;;
        authorizer)
        PROJECTS="--projects apps/core,apps/authorizer"
        break
        ;;
        connector)
        PROJECTS="--projects apps/common,connector"
        break
        ;;
    esac

    GCP_OPTIONS="--env APP_SETTING_GCP_PROJECT=${PROJECT} --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json"
    set -x
    docker exec -it ${GCP_OPTIONS} broker-dev bash -c "mvn test ${PROJECTS} ${MVN_VARS}"
}

function start_dev() {
    set -x
	docker run -it -v $PWD:/base -w /base -p 7070:7070 --detach --name broker-dev ubuntu:18.04 && \
	${docker_cmd} "apps/broker/install-dev.sh"
}

case "$1" in
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
esac