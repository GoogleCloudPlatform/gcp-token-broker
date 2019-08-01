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

package com.google.cloud.broker.endpoints;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;

import com.google.cloud.broker.logging.LoggingUtils;
import com.google.cloud.broker.validation.Validation;
import com.google.cloud.broker.authentication.SessionAuthenticator;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.accesstokens.AccessToken;
import com.google.cloud.broker.accesstokens.AccessTokenCacheFetcher;
import com.google.cloud.broker.protobuf.GetAccessTokenRequest;
import com.google.cloud.broker.protobuf.GetAccessTokenResponse;


public class GetAccessToken {

    public static void run(GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
        Validation.validateNotEmpty("owner", request.getOwner());
        Validation.validateNotEmpty("scope", request.getScope());
        Validation.validateNotEmpty("target", request.getTarget());

        // First try to authenticate the session, if any.
        SessionAuthenticator sessionAuthenticator = new SessionAuthenticator();
        Session session = sessionAuthenticator.authenticateSession();

        if (session == null) {
            // No session token was provided. The client is using direct authentication.
            // So let's authenticate the user.
            AbstractAuthenticationBackend authenticator = AbstractAuthenticationBackend.getInstance();
            String authenticatedUser = authenticator.authenticateUser();

            Validation.validateImpersonator(authenticatedUser, request.getOwner());
        }
        else {
            // A session token was provided. The client is using delegated authentication.
            Validation.validateScope(request.getScope());
            if (!request.getTarget().equals(session.getValue("target"))) {
                throw Status.PERMISSION_DENIED
                    .withDescription("Target mismatch")
                    .asRuntimeException();
            }
            if (!request.getScope().equals(session.getValue("scope"))) {
                throw Status.PERMISSION_DENIED
                    .withDescription("Scope mismatch")
                    .asRuntimeException();
            }
            String sessionOwner = session.getValue("owner").toString();
            String sessionOwnerUsername = sessionOwner.split("@")[0];
            if (!request.getOwner().equals(sessionOwner) && !request.getOwner().equals(sessionOwnerUsername)) {
                throw Status.PERMISSION_DENIED
                    .withDescription("Owner mismatch")
                    .asRuntimeException();
            }
        }

        AccessToken accessToken = (AccessToken) new AccessTokenCacheFetcher(request.getOwner(), request.getScope()).fetch();

        // Log success message
        MDC.put("owner", request.getOwner());
        MDC.put("scope", request.getScope());
        if (session == null) {
            MDC.put("auth_mode", "direct");
        }
        else {
            MDC.put("auth_mode", "delegated");
            MDC.put("session_id", session.getValue("id").toString());
        }
        LoggingUtils.logSuccess(GetAccessToken.class.getSimpleName());

        // Return the response
        GetAccessTokenResponse response = GetAccessTokenResponse.newBuilder()
            .setAccessToken(accessToken.getValue())
            .setExpiresAt(accessToken.getExpiresAt())
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
