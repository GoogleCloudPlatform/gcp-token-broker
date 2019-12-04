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

import com.google.cloud.broker.logging.LoggingUtils;
import com.google.cloud.broker.settings.AppSettings;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;

import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import com.google.cloud.broker.validation.Validation;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.protobuf.GetSessionTokenRequest;
import com.google.cloud.broker.protobuf.GetSessionTokenResponse;


public class GetSessionToken {

    public static void run(GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
        AbstractAuthenticationBackend authenticator = AbstractAuthenticationBackend.getInstance();
        String authenticatedUser = authenticator.authenticateUser();

        Validation.validateParameterNotEmpty("owner", request.getOwner());
        Validation.validateParameterNotEmpty("renewer", request.getRenewer());
        Validation.validateParameterNotEmpty("scope", request.getScope());
        Validation.validateParameterNotEmpty("target", request.getTarget());

        Validation.validateImpersonator(authenticatedUser, request.getOwner());

        // Create session
        Session session = new Session(null, request.getOwner(), request.getRenewer(), request.getTarget(), request.getScope(), null, null, null);
        AbstractDatabaseBackend.getInstance().save(session);

        // Generate session token
        String sessionToken = SessionTokenUtils.marshallSessionToken(session);

        // Log success message
        MDC.put("owner", request.getOwner());
        MDC.put("renewer", request.getRenewer());
        MDC.put("session_id", session.getId());
        LoggingUtils.logSuccess(GetSessionToken.class.getSimpleName());

        // Return response
        GetSessionTokenResponse response = GetSessionTokenResponse.newBuilder()
            .setSessionToken(sessionToken)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
