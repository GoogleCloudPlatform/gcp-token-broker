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

package com.google.cloud.broker.apps.brokerserver.accesstokens;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.AbstractProvider;
import com.google.cloud.broker.caching.CacheFetcher;
import com.google.cloud.broker.settings.AppSettings;


public class AccessTokenCacheFetcher extends CacheFetcher {

    private String owner;
    private Collection<String> scopes;


    public AccessTokenCacheFetcher(String owner, Collection<String> scopes) {
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
        return AbstractProvider.getInstance().getAccessToken(owner, scopes);
    }

    @Override
    protected Object fromJson(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, AccessToken.class);
    }

}
