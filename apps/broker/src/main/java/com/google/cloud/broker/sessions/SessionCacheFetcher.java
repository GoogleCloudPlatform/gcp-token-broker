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

package com.google.cloud.broker.sessions;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.cloud.broker.caching.CacheFetcher;
import com.google.cloud.broker.settings.AppSettings;


public class SessionCacheFetcher extends CacheFetcher {

    private String rawToken;

    private AppSettings settings = AppSettings.getInstance();


    public SessionCacheFetcher(String rawToken) {
        this.rawToken = rawToken;
        // Disable remote cache because the cache key contains sensitive information
        // (i.e. the session tokens)
        this.remoteCacheEnabled = false;
    }

    @Override
    protected String getCacheKey() {
        return String.format("session-%s", rawToken);
    }

    @Override
    protected int getLocalCacheTime() {
        return Integer.parseInt(settings.getProperty("SESSION_LOCAL_CACHE_TIME"));
    }

    @Override
    protected int getRemoteCacheTime() {
        // Remote cache not enabled
        throw new UnsupportedOperationException();
    }

    @Override
    protected String getRemoteCacheCryptoKey() {
        // Remote cache not enabled
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object computeResult() {
        return SessionTokenUtils.getSessionFromRawToken(rawToken);
    }

    @Override
    protected Object fromJson(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, Session.class);
    }

}
