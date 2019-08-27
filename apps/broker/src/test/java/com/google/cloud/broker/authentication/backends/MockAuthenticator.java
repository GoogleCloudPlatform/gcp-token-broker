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

package com.google.cloud.broker.authentication.backends;

import io.grpc.Status;

import com.google.cloud.broker.authentication.AuthorizationHeaderServerInterceptor;

/**
 * Used only for testing. Do NOT use in production.
 * This is a dummy authenticator that returns the provided token as the authenticated user. In other terms, to allow
 * this authenticator to authenticate a user, simply pass the user name in the token, e.g.: "Negotiate: alice@EXAMPLE.COM"
 */
public class MockAuthenticator extends AbstractAuthenticationBackend {

    public MockAuthenticator() {}

    @Override
    public String authenticateUser(String authorizationHeader) {
        if (! authorizationHeader.startsWith("Negotiate ")) {
            throw Status.UNAUTHENTICATED.asRuntimeException();
        }
        String token = authorizationHeader.split("\\s")[1];
        return token;
    }

    @Override
    public String translateName(String name) {
        return name.split("@")[0] + "@altostrat.com";
    }

}