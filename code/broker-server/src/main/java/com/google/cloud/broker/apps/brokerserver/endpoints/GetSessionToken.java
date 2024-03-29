// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.apps.brokerserver.endpoints;

import com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetSessionTokenRequest;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetSessionTokenResponse;
import com.google.cloud.broker.apps.brokerserver.sessions.Session;
import com.google.cloud.broker.apps.brokerserver.sessions.SessionTokenUtils;
import com.google.cloud.broker.apps.brokerserver.validation.GrpcRequestValidation;
import com.google.cloud.broker.apps.brokerserver.validation.ProxyUserValidation;
import com.google.cloud.broker.apps.brokerserver.validation.ScopeValidation;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.protobuf.UnmodifiableLazyStringList;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.slf4j.MDC;

public class GetSessionToken {

  public static void run(
      GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
    MDC.put(LoggingUtils.MDC_METHOD_NAME_KEY, GetSessionToken.class.getSimpleName());

    AbstractAuthenticationBackend authenticator = AbstractAuthenticationBackend.getInstance();
    String authenticatedUser = authenticator.authenticateUser();
    List<String> scopes =
        (List<String>)
            ((UnmodifiableLazyStringList) request.getScopesList())
                .getUnmodifiableView()
                .getUnderlyingElements();

    GrpcRequestValidation.validateParameterNotEmpty("owner", request.getOwner());
    GrpcRequestValidation.validateParameterNotEmpty("renewer", request.getRenewer());
    GrpcRequestValidation.validateParameterNotEmpty("scopes", scopes);
    ScopeValidation.validateScopes(scopes);

    // If the authenticated user requests a session token for another user,
    // verify that it is allowed to do so.
    if (!authenticatedUser.equals(request.getOwner())) {
      ProxyUserValidation.validateImpersonator(authenticatedUser, request.getOwner());
      MDC.put(LoggingUtils.MDC_AUTH_MODE_KEY, LoggingUtils.KDC_AUTH_MODE_VALUE_PROXY);
    } else {
      MDC.put(LoggingUtils.MDC_AUTH_MODE_KEY, LoggingUtils.MDC_AUTH_MODE_VALUE_DIRECT);
    }

    // Create session
    Session session =
        new Session(
            null,
            request.getOwner(),
            request.getRenewer(),
            request.getTarget(),
            String.join(",", scopes),
            null,
            null);
    AbstractDatabaseBackend.getInstance().save(session);

    // Generate session token
    String sessionToken = SessionTokenUtils.marshallSessionToken(session);

    // Log success message
    MDC.put(LoggingUtils.MDC_OWNER_KEY, request.getOwner());
    MDC.put(LoggingUtils.MDC_RENEWER_KEY, request.getRenewer());
    MDC.put(LoggingUtils.MDC_TARGET_KEY, request.getTarget());
    MDC.put(LoggingUtils.MDC_SESSION_ID_KEY, session.getId());
    LoggingUtils.successAuditLog();

    // Return response
    GetSessionTokenResponse response =
        GetSessionTokenResponse.newBuilder().setSessionToken(sessionToken).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
