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
import hmac
import base64
import secrets
import hashlib
import binascii
import json

import grpc

from common import encryption
from common.utils import import_module_from_string
from common.conf import settings

from broker.exceptions import abort
from broker.sessions import Session

try:
    import kerberos
except ImportError:
    pass


TOKEN_SEPARATOR = b'.'


def generate_session_token(session: Session) -> str:
    encrypted_password = encryption.encrypt(settings.ENCRYPTION_DELEGATION_TOKEN_CRYPTO_KEY, session.password)
    header = json.dumps(dict(session_id=session.id)).encode('ascii')
    token = TOKEN_SEPARATOR.join([
        base64.urlsafe_b64encode(header),
        base64.urlsafe_b64encode(encrypted_password)
    ])
    return token.decode('ascii')


def read_session_token(token: str) -> (str, bytes):
    try:
        header, encrypted_password = token.encode('ascii').split(TOKEN_SEPARATOR)
    except (ValueError):
        abort(grpc.StatusCode.UNAUTHENTICATED, f'Invalid session token')
    header = base64.urlsafe_b64decode(header).decode('ascii')
    session_id = json.loads(header)['session_id']
    encrypted_password = base64.urlsafe_b64decode(encrypted_password)
    return session_id, encrypted_password


def password_match(session: Session, encrypted_password: bytes) -> bool:
    decrypted = encryption.decrypt(settings.ENCRYPTION_DELEGATION_TOKEN_CRYPTO_KEY, encrypted_password)
    return hmac.compare_digest(decrypted, session.password)


def get_session_from_token(token: str) -> Session:
    session_id, encrypted_password = read_session_token(token)
    session = Session.get(session_id)
    if not password_match(session, encrypted_password):
        abort(grpc.StatusCode.UNAUTHENTICATED, 'Invalid session token')
    return session


def authenticate_session(context) -> None:
    metadata = context.invocation_metadata()
    header = dict(metadata).get("authorization")
    if header is None or not header.startswith('BrokerSession '):
        return None
    else:
        token = header.split()[1]
        session = get_session_from_token(token)
        if session.is_expired():
            abort(grpc.StatusCode.UNIMPLEMENTED, f'Expired session ID: {session_id}')
        return session


class AuthBackendBase():

    def authenticate(self, request, context):
        raise NotImplementedError


class KerberosAuthBackend(AuthBackendBase):

    def __init__(self):
        if 'KRB5_KTNAME' not in os.environ:
            # Environment variable used by Kerberos to authenticate the broker's principal
            os.environ['KRB5_KTNAME'] = settings.KEYTAB_PATH

    def _get_spnego_token(self, context):
        # Extract the client-supplied SPNEGO token from the "Authorization" header
        # provided in the gRPC request metadata
        metadata = context.invocation_metadata()
        header = dict(metadata).get("authorization")
        if header is None or not header.startswith('Negotiate '):
            abort(grpc.StatusCode.UNAUTHENTICATED, 'Use "authorization: Negotiate <token>" metadata to authenticate')
        spnego_token = header.split()[1]
        return spnego_token

    def authenticate(self, request, context):
        spnego_token = self._get_spnego_token(context)
        gcssapi_context = None
        try:
            # Initialize GSSAPI context
            result, gcssapi_context = kerberos.authGSSServerInit(
                f'{settings.BROKER_SERVICE_NAME}@{settings.BROKER_SERVICE_HOSTNAME}'
            )
            if result != kerberos.AUTH_GSS_COMPLETE:
                # The GSSAPI context initialization failed
                abort(grpc.StatusCode.PERMISSION_DENIED)
            # Process the client-supplied SPNEGO token
            result = kerberos.authGSSServerStep(gcssapi_context, spnego_token)
            if result == kerberos.AUTH_GSS_COMPLETE:
                # Authentication succeded. Return the authenticated principal's username.
                principal = kerberos.authGSSServerUserName(gcssapi_context)
                return principal
            else:
                # Authentication failed
                abort(grpc.StatusCode.PERMISSION_DENIED)
        except kerberos.GSSError:
            abort(grpc.StatusCode.PERMISSION_DENIED)
        finally:
            # Destroy the GSSAPI context
            if gcssapi_context:
                kerberos.authGSSServerClean(gcssapi_context)


def authenticate_user(request, context):
    global auth_backend
    if auth_backend is None:
        klass = import_module_from_string(settings.AUTH_BACKEND)
        auth_backend = klass()
    return auth_backend.authenticate(request, context)


auth_backend = None