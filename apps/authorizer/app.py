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

import sys
sys.path.append('..')  # Make 'common' package discoverable

import json

from flask import Flask, request, session, render_template
from werkzeug.middleware.proxy_fix import ProxyFix
from authlib.flask.client import OAuth
from loginpass import create_flask_blueprint
from loginpass.google import Google, GOOGLE_AUTH_URL

from common.conf import settings
from common.authorization import RefreshToken
from common.utils import validate_domain
from common.encryption import encrypt


app = Flask(__name__)

# Adjust the WSGI environ based on "X-Forwarded-" headers might have been set by upstream proxies
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_host=1, x_proto=1, x_port=1)


# Load secrets
with open(settings.CLIENT_SECRET_PATH) as f:
    client_secret = json.load(f)
with open(settings.FLASK_SECRET_PATH, 'rb') as f:
    flask_secret = f.read()
app.config.update(
    SECRET_KEY=flask_secret,
    GOOGLE_CLIENT_ID=client_secret['web']['client_id'],
    GOOGLE_CLIENT_SECRET=client_secret['web']['client_secret'])


# Configure the Google OAuth backend
Google.OAUTH_CONFIG['authorize_url'] = GOOGLE_AUTH_URL + '&prompt=consent'
Google.OAUTH_CONFIG['client_kwargs'] = {
    'scope': 'openid email profile ' + ' '.join(settings.SCOPE_WHITELIST.split(','))
}


@app.route('/')
def index():
    # If a 'next' URL is provided, then store it into the session
    # to redirect the user after the authorization is complete
    if 'next_url' in request.args:
        session['next_url'] = request.args.get('next_url')
    return render_template('index.html')


def handle_authorize(remote, token, user_info):
    refresh_token_value = token['refresh_token']
    encrypted_value = encrypt(settings.ENCRYPTION_REFRESH_TOKEN_CRYPTO_KEY, refresh_token_value)

    gcp_user = user_info['email']
    validate_domain(gcp_user)

    # Save the refresh token
    refresh_token = RefreshToken(
        id = gcp_user,
        value = encrypted_value
    )
    refresh_token.save()

    # Retrieve next URL from the session, if any
    next_url = session.pop('next_url', None)

    return render_template('success.html', next_url=next_url)


oauth = OAuth(app)
bp = create_flask_blueprint(Google, oauth, handle_authorize)
app.register_blueprint(bp, url_prefix='/google')


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)