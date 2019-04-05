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
import sys
import re
import base64
import json
from datetime import datetime
from unittest.mock import patch

from Crypto.Cipher import XOR
import pytest
import grpc
import requests

# Include parent directories in the python path, so we can then load the `brokerservice` module
sys.path.insert(0, os.path.join(sys.path[0], '/base/apps'))
sys.path.insert(0, os.path.join(sys.path[0], '/base/apps/brokerservice'))

from brokerservice.protobuf.broker_pb2 import (
    GetSessionTokenRequest, GetSessionTokenResponse,
    CancelSessionTokenRequest, CancelSessionTokenResponse,
    RenewSessionTokenRequest, RenewSessionTokenResponse,
    GetAccessTokenRequest, GetAccessTokenResponse)
from common.conf import settings
from common import encryption
from common.utils import datetime_to_integer
from common.authorization import RefreshToken
from broker.authentication import (KerberosAuthBackend, read_session_token, password_match,
    generate_session_token)
from broker.caching import get_cache
from broker.sessions import Session
from broker import exceptions
from broker.endpoints import BrokerServicer
from broker.logging import LoggingBackendBase
from broker import utils
from broker.providers import get_provider


SCOPE = "https://www.googleapis.com/auth/devstorage.read_write"
MOCK_BUCKET = "gs://example"


def mock_unhandled_error(exception, traceback, endpoint, context):
    raise exception


def mock_encrypt(key_id, plaintext):
    xor = XOR.new(key_id)
    return base64.b64encode(xor.encrypt(plaintext))


def mock_decrypt(key_id, ciphertext):
    xor = XOR.new(key_id)
    return xor.decrypt(base64.b64decode(ciphertext)).decode('utf-8')


class MockContext():

    def __init__(self, metadata=None, peer='ipv4:1.1.1.1:9999'):
        self._metadata = metadata or {}
        self._peer = peer

    def set_details(self, details):
        self.details = details

    def set_code(self, code):
        self.code = code

    def abort(self, code, details):
        self.code = code
        self.details = details

    def invocation_metadata(self):
        return self._metadata

    def peer(self):
        return self._peer


class MockKerberosAuthBackend(KerberosAuthBackend):

    def authenticate(self, request, context):
        return self._get_spnego_token(context)


class MockLogger:

    def debug(self, *args, **kwargs):
        pass

    def info(self, *args, **kwargs):
        pass

    def error(self, *args, **kwargs):
        pass

class MockLoggingBackend(LoggingBackendBase):

    def create_logger(self):
        return MockLogger()




# Util functions -----------------------------------------------------------

def monkeypatch_environment(monkeypatch):
    # Patched settings
    monkeypatch.setattr(settings, 'AUTH_BACKEND', 'tests.MockKerberosAuthBackend')
    monkeypatch.setattr(settings, 'LOGGING_BACKEND', 'tests.MockLoggingBackend')
    monkeypatch.setattr(settings, 'DATABASE_BACKEND', 'common.database.DummyDatabaseBackend')
    monkeypatch.setattr(settings, 'PROVIDER_BACKEND', 'broker.providers.ShadowServiceAccountProvider')
    monkeypatch.setattr(settings, 'SCOPE_WHITELIST', SCOPE)
    monkeypatch.setattr(settings, 'SHADOW_PROJECT', 'my-shadow-project')
    monkeypatch.setattr(settings, 'ORIGIN_REALM', 'EXAMPLE.COM')
    monkeypatch.setattr(settings, 'DOMAIN_NAME', 'mydomain.com')

    # Other monkeypatching
    monkeypatch.setattr(exceptions, 'unhandled_error', mock_unhandled_error)
    monkeypatch.setattr(encryption, 'encrypt', mock_encrypt)
    monkeypatch.setattr(encryption, 'decrypt', mock_decrypt)



def get_session_token(authenticated_user, renewer):
    request = GetSessionTokenRequest()
    request.owner = authenticated_user
    request.scope = SCOPE
    request.renewer = renewer
    request.target = MOCK_BUCKET
    context = MockContext({
        'authorization': f'Negotiate {authenticated_user}'
    })
    response = BrokerServicer().GetSessionToken(request, context)
    return response, context


def cancel_session_token(renewer, session_token):
    request = CancelSessionTokenRequest()
    request.session_token = session_token
    context = MockContext({
        'authorization': f'Negotiate {renewer}'
    })
    response = BrokerServicer().CancelSessionToken(request, context)
    return response, context


def renew_session_token(renewer, session_token):
    request = RenewSessionTokenRequest()
    request.session_token = session_token
    context = MockContext({
        'authorization': f'Negotiate {renewer}'
    })
    response = BrokerServicer().RenewSessionToken(request, context)
    return response, context


def get_access_token(scope=SCOPE, authenticated_user=None, owner=None, session_token=None):
    request = GetAccessTokenRequest()
    request.scope = scope
    request.owner = owner
    request.target = MOCK_BUCKET
    if authenticated_user is not None:
        context = MockContext({'authorization': f'Negotiate {authenticated_user}'})
    else:
        context = MockContext({'authorization': f'BrokerSession {session_token}'})
    response = BrokerServicer().GetAccessToken(request, context)
    return response, context


# Tests ---------------------------------------------------------------

def test_UNAUTHENTICATED(monkeypatch):
    monkeypatch_environment(monkeypatch)
    for request, endpoint in [
        (GetSessionTokenRequest(), BrokerServicer().GetSessionToken),
        (RenewSessionTokenRequest(), BrokerServicer().RenewSessionToken),
        (CancelSessionTokenRequest(), BrokerServicer().CancelSessionToken),
        (GetAccessTokenRequest(), BrokerServicer().GetAccessToken),
        ]:
        context = MockContext()
        response = endpoint(request, context)
        assert response is None
        assert context.code == grpc.StatusCode.UNAUTHENTICATED
        assert context.details == 'Use "authorization: Negotiate <token>" metadata to authenticate'


def test_get_session_token(monkeypatch):
    monkeypatch_environment(monkeypatch)
    response, context = get_session_token('alice@EXAMPLE.COM', 'yarn@FOO.BAR')
    assert isinstance(response, GetSessionTokenResponse)
    token = response.session_token
    session_id, encrypted_password = read_session_token(token)
    session = Session.get(session_id)
    assert session.owner == 'alice@EXAMPLE.COM'
    assert session.renewer == 'yarn@FOO.BAR'
    assert password_match(session, encrypted_password)


def test_cancel_session_token_SUCCESS(monkeypatch):
    monkeypatch_environment(monkeypatch)
    session = Session(owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR')
    session.save()
    assert Session.exist(session.id)
    session_token = generate_session_token(session)
    response, context = cancel_session_token('yarn@FOO.BAR', session_token)
    assert isinstance(response, CancelSessionTokenResponse)
    assert not Session.exist(session.id)


def test_cancel_session_token_WRONG_RENEWER(monkeypatch):
    monkeypatch_environment(monkeypatch)
    session = Session(owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR')
    session.save()
    session_token = generate_session_token(session)
    response, context = cancel_session_token('baz@FOO.BAR', session_token)
    assert response is None
    assert Session.exist(session.id)
    assert context.code == grpc.StatusCode.PERMISSION_DENIED
    assert context.details == 'Unauthorized renewer: baz@FOO.BAR'


@patch('broker.sessions.datetime')
def test_renew_session_token_SUCCESS(mock_datetime, monkeypatch):
    monkeypatch_environment(monkeypatch)

    mock_now = datetime(2001, 1, 1)
    mock_datetime.utcnow.return_value = mock_now

    session = Session(owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR')
    session.save()
    assert session.expires_at == datetime_to_integer(mock_now) + settings.SESSION_RENEW_PERIOD

    mock_now = datetime(2002, 2, 2)
    mock_datetime.utcnow.return_value = mock_now

    session_token = generate_session_token(session)
    response, context = renew_session_token('yarn@FOO.BAR', session_token)
    assert isinstance(response, RenewSessionTokenResponse)
    session = Session.get(session.id)
    assert session.expires_at == datetime_to_integer(mock_now) + settings.SESSION_RENEW_PERIOD


def test_rewnew_session_token_WRONG_RENEWER(monkeypatch):
    monkeypatch_environment(monkeypatch)
    session = Session(owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR')
    session.save()
    session_token = generate_session_token(session)
    response, context = renew_session_token('baz@FOO.BAR', session_token)
    assert response is None
    assert Session.exist(session.id)
    assert context.code == grpc.StatusCode.PERMISSION_DENIED
    assert context.details == 'Unauthorized renewer: baz@FOO.BAR'


def test_get_access_token_DIRECT_AUTH_SUCCESS(monkeypatch, authenticated_user='alice@EXAMPLE.COM', owner='alice@EXAMPLE.COM', session_token=None):
    monkeypatch_environment(monkeypatch)

    def mock_get_signed_jwt(google_identity, scope):
        assert google_identity == 'alice-shadow@my-shadow-project.iam.gserviceaccount.com'
        assert scope == SCOPE
        return 'my-signed-jwt'
    def mock_trade_jwt_for_oauth(signed_jwt):
        assert signed_jwt == 'my-signed-jwt'
        return {
            'expires_at': 999999,
            'access_token': 'my-oauth-token'
        }
    provider = get_provider()
    monkeypatch.setattr(provider, 'get_signed_jwt', mock_get_signed_jwt)
    monkeypatch.setattr(provider, 'trade_jwt_for_oauth', mock_trade_jwt_for_oauth)

    response, context = get_access_token(SCOPE, authenticated_user, owner, session_token)
    assert isinstance(response, GetAccessTokenResponse)
    assert response.access_token == 'my-oauth-token'
    assert response.expires_at == 999999

    cached_token = get_cache().get(f'access-token-alice@EXAMPLE.COM-{SCOPE}')
    cached_token = encryption.decrypt(settings.ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY, cached_token)
    cached_token = json.loads(cached_token)
    assert cached_token == {
        'expires_at': 999999,
        'access_token': 'my-oauth-token'
    }


def test_get_access_token_SESSION_TOKEN_AUTH_SUCCESS(monkeypatch):
    monkeypatch_environment(monkeypatch)
    session = Session(
        owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR', target=MOCK_BUCKET, scope=SCOPE)
    session.save()
    session_token = generate_session_token(session)
    test_get_access_token_DIRECT_AUTH_SUCCESS(
        monkeypatch, authenticated_user=None, owner='alice@EXAMPLE.COM', session_token=session_token)


def test_get_access_token_INVALID_SESSION_TOKEN(monkeypatch):
    monkeypatch_environment(monkeypatch)
    request = GetAccessTokenRequest()
    request.scope = SCOPE
    request.target = MOCK_BUCKET
    for session_token in [
            'foobar',
            base64.urlsafe_b64encode(b'foobar').decode('ascii')
        ]:
        context = MockContext({'authorization': f'BrokerSession {session_token}'})
        response = BrokerServicer().GetAccessToken(request, context)
        assert response is None
        assert context.code == grpc.StatusCode.UNAUTHENTICATED
        assert context.details == f'Invalid session token'


def test_get_access_token_SESSION_TOKEN_WRONG_PASSWORD(monkeypatch):
    monkeypatch_environment(monkeypatch)

    session = Session(owner='alice@EXAMPLE.COM', renewer='yarn@FOO.BAR')
    session_token = generate_session_token(session)
    # Change password
    session.password = 'new-password'
    session.save()

    request = GetAccessTokenRequest()
    request.scope = SCOPE
    request.target = MOCK_BUCKET
    context = MockContext({'authorization': f'BrokerSession {session_token}'})
    response = BrokerServicer().GetAccessToken(request, context)
    assert response is None
    assert context.code == grpc.StatusCode.UNAUTHENTICATED
    assert context.details == f'Invalid session token'