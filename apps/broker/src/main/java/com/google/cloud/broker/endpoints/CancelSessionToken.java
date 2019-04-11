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
import com.google.cloud.broker.authentication.SpnegoAuthenticator;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.protobuf.CancelSessionTokenRequest;
import com.google.cloud.broker.protobuf.CancelSessionTokenResponse;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import com.google.cloud.broker.validation.Validation;


public class CancelSessionToken {

    public static void run(CancelSessionTokenRequest request, StreamObserver<CancelSessionTokenResponse> responseObserver) {
        SpnegoAuthenticator authenticator = new SpnegoAuthenticator();
        String authenticatedUser = authenticator.authenticateUser();

        Validation.validateNotEmpty("session_token", request.getSessionToken());

        // Retrieve the session details from the database
        Session session = SessionTokenUtils.getSessionFromToken(request.getSessionToken());

        // Verify that the caller is the authorized renewer for the toke
        if (!session.getValue("renewer").equals(authenticatedUser)) {
            throw Status.PERMISSION_DENIED.withDescription(String.format("Unauthorized renewer: %s", authenticatedUser)).asRuntimeException();
        }

        // Cancel the token
        Model.delete(session);

        // Log success message
        MDC.put("owner", (String) session.getValue("owner"));
        MDC.put("renewer", (String) session.getValue("renewer"));
        MDC.put("session_id", (String) session.getValue("id"));
        LoggingUtils.logSuccess(CancelSessionToken.class.getSimpleName());

        // Return response
        CancelSessionTokenResponse response = CancelSessionTokenResponse.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
