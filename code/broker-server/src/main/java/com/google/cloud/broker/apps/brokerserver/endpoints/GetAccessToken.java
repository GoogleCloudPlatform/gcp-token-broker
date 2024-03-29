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

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessTokenCacheFetcher;
import com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetAccessTokenRequest;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetAccessTokenResponse;
import com.google.cloud.broker.apps.brokerserver.sessions.Session;
import com.google.cloud.broker.apps.brokerserver.sessions.SessionAuthenticator;
import com.google.cloud.broker.apps.brokerserver.validation.GrpcRequestValidation;
import com.google.cloud.broker.apps.brokerserver.validation.ProxyUserValidation;
import com.google.cloud.broker.apps.brokerserver.validation.ScopeValidation;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;

public class GetAccessToken {

  public static void run(
      GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
    MDC.put(LoggingUtils.MDC_METHOD_NAME_KEY, GetAccessToken.class.getSimpleName());

    // First try to authenticate the session, if any.
    SessionAuthenticator sessionAuthenticator = new SessionAuthenticator();
    Session session = sessionAuthenticator.authenticateSession();

    // Fetch parameters from the request
    String owner = request.getOwner();
    List<String> scopes = request.getScopesList();
    String target = request.getTarget();

    if (session == null) { // No session token was provided. The client is using direct or proxy
      // authentication.
      // Assert that the required parameters were provided and are valid
      GrpcRequestValidation.validateParameterNotEmpty("owner", owner);
      GrpcRequestValidation.validateParameterNotEmpty("scopes", scopes);
      ScopeValidation.validateScopes(scopes);

      // No session token was provided. The client is using direct authentication.
      // So let's authenticate the user.
      AbstractAuthenticationBackend authenticator = AbstractAuthenticationBackend.getInstance();
      String authenticatedUser = authenticator.authenticateUser();

      // If the authenticated user requests an access token for another user,
      // verify that it is allowed to do so.
      if (!authenticatedUser.equals(owner)) {
        ProxyUserValidation.validateImpersonator(authenticatedUser, owner);
        MDC.put(LoggingUtils.MDC_AUTH_MODE_KEY, LoggingUtils.KDC_AUTH_MODE_VALUE_PROXY);
      } else {
        MDC.put(LoggingUtils.MDC_AUTH_MODE_KEY, LoggingUtils.MDC_AUTH_MODE_VALUE_DIRECT);
      }
    } else { // A session token was provided. The client is using delegated authentication.
      MDC.put(LoggingUtils.MDC_AUTH_MODE_KEY, LoggingUtils.MDC_AUTH_MODE_VALUE_DELEGATED);
      MDC.put(LoggingUtils.MDC_AUTH_MODE_DELEGATED_SESSION_ID_KEY, session.getId());

      // Assert that no parameters were provided
      GrpcRequestValidation.validateParameterIsEmpty("owner", owner);
      GrpcRequestValidation.validateParameterIsEmpty("scopes", scopes);
      GrpcRequestValidation.validateParameterIsEmpty("target", target);

      // Fetch the correct parameters from the session
      owner = session.getOwner();
      target = session.getTarget();
      scopes = Arrays.asList(session.getScopes().split(","));
    }

    // Fetch the access token
    AccessToken accessToken =
        (AccessToken) new AccessTokenCacheFetcher(owner, scopes, target).fetch();

    // Log success message
    MDC.put(LoggingUtils.MDC_OWNER_KEY, owner);
    MDC.put(LoggingUtils.MDC_SCOPES_KEY, String.join(",", scopes));
    MDC.put(LoggingUtils.MDC_TARGET_KEY, target);
    LoggingUtils.successAuditLog();

    // Return the response
    GetAccessTokenResponse response =
        GetAccessTokenResponse.newBuilder()
            .setAccessToken(accessToken.getValue())
            .setExpiresAt(accessToken.getExpiresAt())
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
