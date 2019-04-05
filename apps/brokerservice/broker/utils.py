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

import json
from datetime import datetime, timedelta

import grpc
import requests

from common.conf import settings
import common.utils as common_utils
from broker.exceptions import abort


def validate_not_empty(request, param):
    if getattr(request, param) == '':
        abort(grpc.StatusCode.INVALID_ARGUMENT, f'Request must provide the `{param}` parameter')


def validate_scope(scope):
    # Check if the given scope is whitelisted
    scope_set = set(scope.split(','))
    whitelist = set(settings.SCOPE_WHITELIST.split(','))
    if not scope_set.issubset(whitelist):
        abort(grpc.StatusCode.PERMISSION_DENIED, f'`{scope}` is not a whitelisted scope')


def validate_domain(user):
    try:
        common_utils.validate_domain(user)
    except ValueError:
        abort(grpc.StatusCode.PERMISSION_DENIED, f'Invalid domain for user: {user}')


def validate_impersonator(impersonator, impersonated):
    whitelist = set(settings.PROXY_USER_WHITELIST.split(','))
    if impersonator != impersonated and impersonator not in whitelist:
        abort(grpc.StatusCode.PERMISSION_DENIED, f'`{impersonator}` is not a whitelisted impersonator')


def enrich_log_data(log_data, request, context):
    log_data.update({
        'client': context.peer()
    })