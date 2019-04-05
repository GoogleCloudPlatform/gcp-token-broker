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
import json
from datetime import datetime, timedelta

import requests
from google_auth_oauthlib.helpers import session_from_client_secrets_file
from oauthlib.oauth2.rfc6749.errors import InvalidGrantError
import grpc

from common import encryption
from common.conf import settings
from common.utils import datetime_to_integer, import_module_from_string
from common.database import DatabaseObjectNotFound
from common.authorization import RefreshToken
from broker.exceptions import abort


class AccessTokenProviderBase():
    """
    Base class for all access token providers.
    """

    def calculate_expiry_time(self, expires_in):
        # Calculate expiry date+time in milliseconds
        now = datetime.utcnow()
        return datetime_to_integer(now + timedelta(seconds=expires_in))

    def get_access_token(owner, scope):
        raise NotImplementedError


class SignedJWTProviderBase(AccessTokenProviderBase):
    """
    Base class for access token providers that rely on signed JWTs.
    """

    broker_issuer = False

    def get_broker_service_account_details(self):
        """
        Returns the broker's service account's email and access token
        """
        # Get service account's email from the VM's metadata server
        response = requests.get(
            url='http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email',
            headers={'Metadata-Flavor': 'Google'}
        )
        email = response.text
        # Get service account's access token from the VM's metadata server
        response = requests.get(
            url='http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token',
            headers={'Metadata-Flavor': 'Google'}
        )
        access_token = response.json()['access_token']
        return email, access_token

    def get_signed_jwt(self, google_identity, scope):
        """
        Returns a signed JWT for the given scope and Google identity.
        """
        # Get the broker's service account email and access token
        broker_email, broker_token = self.get_broker_service_account_details()

        # Create a JWT
        iat = datetime.utcnow()
        exp = iat + timedelta(seconds=settings.JWT_LIFE)
        jwt = {
            'scope': scope,
            'aud': 'https://www.googleapis.com/oauth2/v4/token',
            'iat': int(iat.timestamp()),
            'exp': int(exp.timestamp()),
        }

        if self.broker_issuer:
            jwt['sub'] = google_identity
            jwt['iss'] = broker_email
            service_account = broker_email
        else:
            jwt['iss'] = google_identity
            service_account = google_identity

        # Sign the JWT
        response = requests.post(
            url='https://iam.googleapis.com/v1/projects/-/serviceAccounts/' + service_account + ':signJwt',
            headers={'Authorization': 'Bearer ' + broker_token},
            data={'payload': json.dumps(jwt)}
        )
        response = response.json()
        if 'error' in response:
            raise Exception(response['error']['message'])
        return response['signedJwt']

    def trade_jwt_for_oauth(self, signed_jwt):
        """
        Obtains a new Oauth token based on the given signed JWT.
        """
        # Record the current time so we can later calculate the token's expiry date
        now = datetime.utcnow()

        # Trade the JWT for an Oauth token
        response = requests.post(
        url='https://www.googleapis.com/oauth2/v4/token',
        data={
            'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion': signed_jwt
        })
        response = response.json()

        return {
            'access_token': response['access_token'],
            'expires_at': self.calculate_expiry_time(response['expires_in'])
        }

    def get_access_token(self, owner, scope):
        # First, get the corresponding Google identity
        google_identity = self.get_google_identity(owner)
        # Generate a signed JWT
        signed_jwt = self.get_signed_jwt(google_identity, scope)
        # Obtain a new Oauth token
        access_token = self.trade_jwt_for_oauth(signed_jwt)
        return access_token


class ShadowServiceAccountProvider(SignedJWTProviderBase):
    """
    Provider that generates access tokens for shadow service accounts.
    A shadow service account is a service account that is associated
    with a Google user.
    For this provider to work, the "Token Creator" IAM role must be given
    to the broker's service account for each one of the delegated shadow
    service accounts.
    """

    broker_issuer = False

    def get_google_identity(self, identity):
        """
        Maps the provided identity the corresponding Google shadow account.
        """
        username = identity.split('@')[0]
        project = settings.SHADOW_PROJECT
        return f'{username}-shadow@{project}.iam.gserviceaccount.com'


class DomainWideDelegationAuthorityProvider(SignedJWTProviderBase):
    """
    Provider that generates access tokens for any users within the domain.
    For this provider to work, the broker's service account must be given
    domain-wide delegation authority. Also, the "Token Creator" IAM role must
    be given to the broker's service account for itself.
    """

    broker_issuer = True

    def get_google_identity(self, identity):
        username = identity.split('@')[0]
        return f'{username}@{settings.DOMAIN_NAME}'


class RefreshTokenProvider(AccessTokenProviderBase):
    """
    Provider that uses refresh tokens to generate access tokens
    on behalf of users. This providers requires the use of the
    authorizer app to obtain refresh tokens.
    """

    def __init__(self):
        # Prevent oauthlib from raising a warning exception about scope change. This
        # warning would otherwise be raised because the provider doesn't need the
        # client's default scopes ('userinfo.email' and 'plus.me').
        # See: https://github.com/oauthlib/oauthlib/blob/master/oauthlib/oauth2/rfc6749/parameters.py#L450
        os.environ['OAUTHLIB_RELAX_TOKEN_SCOPE'] = 'true'

    AUTHZ_ERROR_MESSAGE = 'GCP Token Broker authorization is invalid or has expired for user: {}'

    def get_google_identity(self, identity):
        username = identity.split('@')[0]
        return f'{username}@{settings.DOMAIN_NAME}'

    def get_access_token(self, owner, scope):
        google_identity = self.get_google_identity(owner)

        try:
            refresh_token = RefreshToken.get(google_identity)
        except DatabaseObjectNotFound:
            # The user has not authorized the broker yet
            abort(grpc.StatusCode.PERMISSION_DENIED, self.AUTHZ_ERROR_MESSAGE.format(owner))

        oauthsession, client_config = session_from_client_secrets_file(
            settings.CLIENT_SECRET_PATH,
            scopes=scope.split(',')
        )
        decrypted_value = encryption.decrypt(settings.ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY, refresh_token.value)

        try:
            access_token = oauthsession.refresh_token(
                token_url='https://oauth2.googleapis.com/token',
                client_id=client_config['web']['client_id'],
                client_secret=client_config['web']['client_secret'],
                refresh_token=decrypted_value)
        except InvalidGrantError:
            # The refresh token has expired or has been revoked
            abort(grpc.StatusCode.PERMISSION_DENIED, self.AUTHZ_ERROR_MESSAGE.format(owner))

        return {
            'access_token': access_token['access_token'],
            'expires_at': self.calculate_expiry_time(access_token['expires_in'])
        }


def get_provider():
    global _provider
    if _provider is None:
        klass = import_module_from_string(settings.PROVIDER_BACKEND)
        _provider = klass()
    return _provider


_provider = None