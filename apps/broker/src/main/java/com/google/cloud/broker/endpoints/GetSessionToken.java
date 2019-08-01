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

import java.util.HashMap;

import com.google.cloud.broker.logging.LoggingUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;

import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import com.google.cloud.broker.validation.Validation;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import com.google.cloud.broker.protobuf.GetSessionTokenRequest;
import com.google.cloud.broker.protobuf.GetSessionTokenResponse;


public class GetSessionToken {

    public static void run(GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
        AbstractAuthenticationBackend authenticator = AbstractAuthenticationBackend.getInstance();
        String authenticatedUser = authenticator.authenticateUser();

        Validation.validateNotEmpty("owner", request.getOwner());
        Validation.validateNotEmpty("renewer", request.getRenewer());
        Validation.validateNotEmpty("scope", request.getScope());
        Validation.validateNotEmpty("target", request.getTarget());

        Validation.validateImpersonator(authenticatedUser, request.getOwner());

        // Create session
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", request.getOwner());
        values.put("renewer", request.getRenewer());
        values.put("target", request.getTarget());
        values.put("scope", request.getScope());
        Session session = new Session(values);
        Model.save(session);

        // Generate session token
        String sessionToken = SessionTokenUtils.marshallSessionToken(session);

        // Log success message
        MDC.put("owner", request.getOwner());
        MDC.put("renewer", request.getRenewer());
        MDC.put("session_id", session.getValue("id").toString());
        LoggingUtils.logSuccess(GetSessionToken.class.getSimpleName());

        // Return response
        GetSessionTokenResponse response = GetSessionTokenResponse.newBuilder()
            .setSessionToken(sessionToken)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
