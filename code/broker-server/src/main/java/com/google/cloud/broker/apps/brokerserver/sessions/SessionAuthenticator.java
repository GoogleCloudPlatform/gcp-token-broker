// Copyright 2019 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.apps.brokerserver.sessions;

import com.google.cloud.broker.authentication.AuthorizationHeaderServerInterceptor;
import io.grpc.Status;


public class SessionAuthenticator {

    public Session authenticateSession() {
        String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();

        // Make sure this is indeed
        if (! authorizationHeader.startsWith("BrokerSession ")) {
            return null;
        }

        // Extract the session token from the authorization header
        String token = authorizationHeader.split("\\s")[1];

        Session session = (Session) new SessionCacheFetcher(token).fetch();

        if (session.isExpired()) {
            throw Status.UNAUTHENTICATED.withDescription("Expired session ID: " + session.getId()).asRuntimeException();
        }

        return session;
    }
}
