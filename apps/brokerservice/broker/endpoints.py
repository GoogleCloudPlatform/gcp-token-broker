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

import os
import traceback
import json
import uuid
from datetime import timedelta

import grpc
import requests

from common.conf import settings
from common.database import DatabaseObjectNotFound
from common import encryption
from brokerservice.protobuf import broker_pb2, broker_pb2_grpc
from broker.logging import get_logger
from broker.authentication import (authenticate_user, authenticate_session,
    generate_session_token, get_session_from_token)
from broker import exceptions
from broker.exceptions import abort, BrokerGRPCException
from broker.caching import get_cache, get_local_cache
from broker.sessions import Session
from broker import utils
from broker.providers import get_provider


def get_session_token(request, context):
    authenticated_user = authenticate_user(request, context)
    utils.validate_not_empty(request, 'owner')
    utils.validate_not_empty(request, 'scope')

    owner = request.owner or authenticated_user
    renewer = request.renewer
    target = request.target
    scope = request.scope

    utils.validate_impersonator(authenticated_user, owner)

    # Create and save session
    session = Session(
        owner=owner,
        renewer=renewer,
        target=target,
        scope=scope,
    )
    session.save()

    # Create and return RPC response
    response = broker_pb2.GetSessionTokenResponse()
    response.session_token = generate_session_token(session)
    return response, {'owner': session.owner, 'renewer': session.renewer, 'session-id': session.id}


def renew_session_token(request, context):
    authenticated_user = authenticate_user(request, context)
    utils.validate_not_empty(request, 'session_token')

    # Retrieve the session details from the database
    try:
        session = get_session_from_token(request.session_token)
    except DatabaseObjectNotFound:
        abort(grpc.StatusCode.PERMISSION_DENIED, 'Session token is invalid or has expired')

    # Verify that the caller is the authorized renewer for the token
    if session.renewer != authenticated_user:
        abort(grpc.StatusCode.PERMISSION_DENIED, f'Unauthorized renewer: {authenticated_user}')

    # Extends session's lifetime
    session.extend_lifetime()
    session.save()

    # Create and return RPC response
    response = broker_pb2.RenewSessionTokenResponse()
    response.expires_at = session.expires_at
    return response, {'owner': session.owner, 'renewer': session.renewer, 'session-id': session.id}


def cancel_session_token(request, context):
    authenticated_user = authenticate_user(request, context)
    utils.validate_not_empty(request, 'session_token')

    # Retrieve the session details from the database
    try:
        session = get_session_from_token(request.session_token)
    except DatabaseObjectNotFound:
        abort(grpc.StatusCode.PERMISSION_DENIED, 'Session token is invalid or has expired')

    # Verify that the caller is the authorized renewer for the token
    # (The renewer can also cancel the token)
    if session.renewer != authenticated_user:
        abort(grpc.StatusCode.PERMISSION_DENIED, f'Unauthorized renewer: {authenticated_user}')

    # Cancel the token
    session.delete()

    # Create and return RPC response
    response = broker_pb2.CancelSessionTokenResponse()
    return response, {'owner': session.owner, 'renewer': session.renewer, 'session-id': session.id}


def get_access_token(request, context):
    session = authenticate_session(context)
    if session is not None:
        utils.validate_not_empty(request, 'owner')
        utils.validate_not_empty(request, 'scope')
        if request.target != session.target:
            abort(grpc.StatusCode.PERMISSION_DENIED, "Target mismatch")
        if request.owner not in [session.owner, session.owner.split('@')[0]]:
            abort(grpc.StatusCode.PERMISSION_DENIED, "Owner mismatch")
        if request.scope != session.scope:
            abort(grpc.StatusCode.PERMISSION_DENIED, "Scope mismatch")
    else:
        authenticated_user = authenticate_user(request, context)
        utils.validate_not_empty(request, 'owner')
        utils.validate_not_empty(request, 'scope')
        utils.validate_impersonator(authenticated_user, request.owner)

    utils.validate_scope(request.scope)

    # Create cache key to look up access token from cache
    cache_key = f'access-token-{request.owner}-{request.scope}'

    # First check in local cache
    local_cache = get_local_cache()
    access_token = local_cache.get(cache_key)
    if access_token is None:
        # Not found in local cache, so look in remote cache.
        cache = get_cache()
        encrypted_access_token = cache.get(cache_key)
        if encrypted_access_token is not None:
            # Cache hit... Let's load the value.
            access_token_json = encryption.decrypt(settings.ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY, encrypted_access_token)
            access_token = json.loads(access_token_json)
        else:
            # Cache miss... Let's generate a new access token.
            # Start by acquiring a lock to avoid cache stampede
            lock = cache.acquire_lock(f'{cache_key}_lock')
            try:
                # Check again if there's still no value
                encrypted_access_token = cache.get(cache_key)
                if encrypted_access_token is not None:
                    # This time it's a cache hit. The token must have been generated
                    # by a competing thread. So we just load the value.
                    access_token_json = encryption.decrypt(settings.ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY, encrypted_access_token)
                    access_token = json.loads(access_token_json)
                else:
                    # Again a cache miss, so we must really generate a new token now.
                    access_token = get_provider().get_access_token(request.owner, request.scope)
                    # Cache token for possible future requests
                    access_token_json = json.dumps(access_token)
                    encrypted_value = encryption.encrypt(settings.ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY, access_token_json)
                    cache.set(cache_key, encrypted_value, timedelta(seconds=settings.ACCESS_TOKEN_REMOTE_CACHE_TIME))
            finally:
                # Release the lock
                cache.release_lock(lock)

        # Add unencrypted token to local cache
        local_cache.set(cache_key, access_token, settings.ACCESS_TOKEN_LOCAL_CACHE_TIME)

    # Create and return RPC response
    response = broker_pb2.GetAccessTokenResponse()
    response.access_token = access_token['access_token']
    response.expires_at = access_token['expires_at']
    return response, {'owner': request.owner, 'scope': request.scope}


class BrokerServicer(broker_pb2_grpc.BrokerServicer):

    def call_enpoint(self, endpoint, request, context):
        try:
            response, log_data = endpoint.__call__(request, context)
            log_data['responseType'] = 'success'
            utils.enrich_log_data(log_data, request, context)
            get_logger().info(endpoint.__name__, extra=log_data)
            return response
        except BrokerGRPCException as e:
            exceptions.handled_error(e, traceback, endpoint, request, context)
        except Exception as e:
            exceptions.unhandled_error(e, traceback, endpoint, request, context)

    def GetSessionToken(self, request, context):
        return self.call_enpoint(get_session_token, request, context)

    def RenewSessionToken(self, request, context):
        return self.call_enpoint(renew_session_token, request, context)

    def CancelSessionToken(self, request, context):
        return self.call_enpoint(cancel_session_token, request, context)

    def GetAccessToken(self, request, context):
        return self.call_enpoint(get_access_token, request, context)
