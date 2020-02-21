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

package com.google.cloud.broker.apps.brokerserver.accesstokens;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import org.slf4j.MDC;

import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.AbstractProvider;
import com.google.cloud.broker.apps.brokerserver.validation.Validation;
import com.google.cloud.broker.caching.CacheFetcher;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.usermapping.AbstractUserMapper;


public class AccessTokenCacheFetcher extends CacheFetcher {

    private String owner;
    private List<String> scopes;


    public AccessTokenCacheFetcher(String owner, List<String> scopes) {
        this.owner = owner;
        this.scopes = scopes;
    }

    @Override
    protected String getCacheKey() {
        return String.format("access-token-%s-%s", owner, scopes);
    }

    @Override
    protected int getLocalCacheTime() {
        return AppSettings.getInstance().getInt(AppSettings.ACCESS_TOKEN_LOCAL_CACHE_TIME);
    }

    @Override
    protected int getRemoteCacheTime() {
        return AppSettings.getInstance().getInt(AppSettings.ACCESS_TOKEN_REMOTE_CACHE_TIME);
    }

    @Override
    protected Object computeResult() {
        String googleIdentity;
        try {
            googleIdentity = AbstractUserMapper.getInstance().map(owner);
            Validation.validateEmail(googleIdentity);
        }
        catch (IllegalArgumentException e) {
            throw Status.PERMISSION_DENIED.withDescription("Principal `" + owner + "` cannot be matched to a Google identity.").asRuntimeException();
        }
        MDC.put("access_token_user", googleIdentity);
        return AbstractProvider.getInstance().getAccessToken(googleIdentity, scopes);
    }

    @Override
    protected Object fromJson(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, AccessToken.class);
    }

}
