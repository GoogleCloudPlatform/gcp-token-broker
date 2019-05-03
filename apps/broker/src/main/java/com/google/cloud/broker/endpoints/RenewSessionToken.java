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

import com.google.cloud.broker.sessions.SessionTokenUtils;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;

import com.google.cloud.broker.authentication.SpnegoAuthenticator;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.protobuf.RenewSessionTokenRequest;
import com.google.cloud.broker.protobuf.RenewSessionTokenResponse;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionCacheFetcher;
import com.google.cloud.broker.logging.LoggingUtils;
import com.google.cloud.broker.validation.Validation;


public class RenewSessionToken {

    public static void run(RenewSessionTokenRequest request, StreamObserver<RenewSessionTokenResponse> responseObserver) {
        SpnegoAuthenticator authenticator = SpnegoAuthenticator.getInstance();
        String authenticatedUser = authenticator.authenticateUser();

        Validation.validateNotEmpty("session_token", request.getSessionToken());

        // Retrieve the session details from the database
        Session session = SessionTokenUtils.getSessionFromRawToken(request.getSessionToken());

        // Verify that the caller is the authorized renewer for the toke
        if (!session.getValue("renewer").equals(authenticatedUser)) {
            throw Status.PERMISSION_DENIED.withDescription(String.format("Unauthorized renewer: %s", authenticatedUser)).asRuntimeException();
        }

        // Extend session's lifetime
        session.extendLifetime();
        Model.save(session);

        // Log success message
        MDC.put("owner", (String) session.getValue("owner"));
        MDC.put("renewer", (String) session.getValue("renewer"));
        MDC.put("session_id", (String) session.getValue("id"));
        LoggingUtils.logSuccess(RenewSessionToken.class.getSimpleName());

        // Return response
        RenewSessionTokenResponse response = RenewSessionTokenResponse.newBuilder()
            .setExpiresAt((long) session.getValue("expires_at"))
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
