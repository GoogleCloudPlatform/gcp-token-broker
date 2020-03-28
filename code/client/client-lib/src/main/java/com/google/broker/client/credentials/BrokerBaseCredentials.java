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

package com.google.broker.client.credentials;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.Date;

import com.google.api.client.util.Clock;
import com.google.auth.http.AuthHttpConstants;
import com.google.auth.oauth2.AccessToken;
import com.google.common.base.Preconditions;
import com.google.auth.RequestMetadataCallback;
import com.google.auth.Credentials;

import com.google.broker.client.connect.BrokerServerInfo;
import com.google.broker.client.connect.BrokerGateway;


public abstract class BrokerBaseCredentials extends Credentials {

    private static final long MINIMUM_TOKEN_MILLISECONDS = 60000L * 5L;
    private final Object lock = new byte[0];
    private Map<String, List<String>> requestMetadata;
    protected BrokerServerInfo serverInfo;
    private AccessToken cachedAccessToken;
    transient Clock clock = Clock.SYSTEM;

    public BrokerBaseCredentials(BrokerServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    @Override
    public String getAuthenticationType() {
        return null;
    }

    @Override
    public void getRequestMetadata(
        final URI uri, Executor executor, final RequestMetadataCallback callback) {
        Map<String, List<String>> metadata;
        synchronized (lock) {
            if (shouldRefresh()) {
                // The base class implementation will do a blocking get in the executor.
                super.getRequestMetadata(uri, executor, callback);
                return;
            }
            metadata = Preconditions.checkNotNull(requestMetadata, "cached requestMetadata");
        }
        callback.onSuccess(metadata);
    }

    @Override
    public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
        synchronized (lock) {
            if (shouldRefresh()) {
                refresh();
            }
            return Preconditions.checkNotNull(requestMetadata, "requestMetadata");
        }
    }

    private boolean shouldRefresh() {
        Long expiresIn = getExpiresInMilliseconds();
        return requestMetadata == null || expiresIn != null && expiresIn <= MINIMUM_TOKEN_MILLISECONDS;
    }

    private Long getExpiresInMilliseconds() {
        if (cachedAccessToken == null) {
            return null;
        }
        Date expirationTime = cachedAccessToken.getExpirationTime();
        if (expirationTime == null) {
            return null;
        }
        return (expirationTime.getTime() - clock.currentTimeMillis());
    }

    @Override
    public boolean hasRequestMetadata() {
        return true;
    }

    @Override
    public boolean hasRequestMetadataOnly() {
        return true;
    }

    @Override
    public void refresh() throws IOException {
        synchronized (lock) {
            requestMetadata = null;
            cachedAccessToken = null;
            useAccessToken(Preconditions.checkNotNull(refreshAccessToken(), "new access token"));
        }
    }

    private void useAccessToken(AccessToken token) {
        this.cachedAccessToken = token;
        this.requestMetadata =
            Collections.singletonMap(
                AuthHttpConstants.AUTHORIZATION,
                Collections.singletonList(BrokerGateway.REQUEST_AUTH_HEADER + " " + token.getTokenValue()));
    }

    public final AccessToken getAccessToken() {
        return cachedAccessToken;
    }

    public abstract AccessToken refreshAccessToken();
}
