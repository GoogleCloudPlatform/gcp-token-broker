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
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.authentication.SessionAuthenticator;
import com.google.cloud.broker.caching.AbstractRemoteCache;
import com.google.cloud.broker.caching.LocalCache;
import com.google.cloud.broker.encryption.EncryptionUtils;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.providers.AccessToken;
import com.google.cloud.broker.authentication.SpnegoAuthenticator;
import com.google.cloud.broker.protobuf.GetAccessTokenRequest;
import com.google.cloud.broker.protobuf.GetAccessTokenResponse;
import com.google.cloud.broker.providers.AbstractProvider;

import java.util.concurrent.locks.Lock;


public class GetAccessToken {

    public static void run(GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
        // First try to authenticate the session, if any.
        SessionAuthenticator sessionAuthenticator = new SessionAuthenticator();
        Session session = sessionAuthenticator.authenticateSession();

        if (session == null) {
            // No session token was provided. The client is using direct authentication.
            // So let's authenticate the user.
            SpnegoAuthenticator spnegoAuthenticator = new SpnegoAuthenticator();
            String authenticatedUser = spnegoAuthenticator.authenticateUser();
            Validation.validateNotEmpty("owner", request.getOwner());
            Validation.validateNotEmpty("scope", request.getScope());
            Validation.validateScope(request.getScope());
            Validation.validateImpersonator(authenticatedUser, request.getOwner());
        }
        else {
            // A session token was provided. The client is using delegated authentication.
            Validation.validateNotEmpty("owner", request.getOwner());
            Validation.validateNotEmpty("scope", request.getScope());
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
            String sessionOwner = (String) session.getValue("owner");
            String sessionOwnerUsername = sessionOwner.split("@")[0];
            if (!request.getOwner().equals(sessionOwner) && !request.getOwner().equals(sessionOwnerUsername)) {
                throw Status.PERMISSION_DENIED
                    .withDescription("Owner mismatch")
                    .asRuntimeException();
            }
        }

        // Create cache key to look up access token from cache
        String cacheKey = String.format("access-token-%s-%s", request.getOwner(), request.getScope());

        // First check in local cache
        AccessToken accessToken = (AccessToken) LocalCache.get(cacheKey);

        AppSettings settings = AppSettings.getInstance();
        if (accessToken == null) {
            // Not found in local cache, so look in remote cache.
            AbstractRemoteCache cache = AbstractRemoteCache.getInstance();
            byte[] encryptedAccessToken = cache.get(cacheKey);
            if (encryptedAccessToken != null) {
                // Cache hit... Let's load the value.
                String cryptoKey = settings.getProperty("ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY");
                String json = new String(EncryptionUtils.decrypt(cryptoKey, encryptedAccessToken));
                accessToken = AccessToken.fromJSON(json);
            }
            else {
                // Cache miss... Let's generate a new access token.
                // Start by acquiring a lock to avoid cache stampede
                Lock lock = cache.acquireLock(cacheKey + "_lock");

                // Check again if there's still no value
                encryptedAccessToken = cache.get(cacheKey);
                if (encryptedAccessToken != null) {
                    // This time it's a cache hit. The token must have been generated
                    // by a competing thread. So we just load the value.
                    String cryptoKey = settings.getProperty("ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY");
                    String json = new String(EncryptionUtils.decrypt(cryptoKey, encryptedAccessToken));
                    accessToken = AccessToken.fromJSON(json);
                }
                else {
                    accessToken = AbstractProvider.getInstance().getAccessToken(request.getOwner(), request.getScope());
                    // Encrypt and cache token for possible future requests
                    String json = accessToken.toJSON();
                    encryptedAccessToken = EncryptionUtils.encrypt(
                            settings.getProperty("ENCRYPTION_ACCESS_TOKEN_CACHE_CRYPTO_KEY"),
                            json.getBytes()
                    );
                    cache.set(cacheKey, encryptedAccessToken, Integer.parseInt(settings.getProperty("ACCESS_TOKEN_REMOTE_CACHE_TIME")));
                }

                // Release the lock
                lock.unlock();
            }

            // Add unencrypted token to local cache
            LocalCache.set(new String(cacheKey), accessToken, Integer.parseInt(settings.getProperty("ACCESS_TOKEN_LOCAL_CACHE_TIME")));
        }

        // Log success message
        MDC.put("owner", request.getOwner());
        MDC.put("scope", request.getScope());
        if (session == null) {
            MDC.put("auth_mode", "direct");
        }
        else {
            MDC.put("auth_mode", "delegated");
            MDC.put("session_id", (String) session.getValue("id"));
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
