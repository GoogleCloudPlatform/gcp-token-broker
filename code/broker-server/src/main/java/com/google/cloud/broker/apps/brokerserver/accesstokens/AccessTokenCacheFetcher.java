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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.AbstractProvider;
import com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils;
import com.google.cloud.broker.caching.CacheFetcher;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.usermapping.AbstractUserMapper;
import com.google.cloud.broker.validation.EmailValidation;
import io.grpc.Status;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;

public class AccessTokenCacheFetcher extends CacheFetcher {

  private String owner;
  private List<String> scopes;
  private String target;

  public AccessTokenCacheFetcher(String owner, List<String> scopes, String target) {
    this.owner = owner;
    this.scopes = scopes;
    this.target = target;
  }

  @Override
  protected String getCacheKey() {
    return String.format("access-token-%s-%s-%s", owner, scopes, target);
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
      EmailValidation.validateEmail(googleIdentity);
    } catch (IllegalArgumentException e) {
      throw Status.PERMISSION_DENIED
          .withDescription("Principal `" + owner + "` cannot be mapped to a Google identity.")
          .asRuntimeException();
    }
    MDC.put(LoggingUtils.MDC_ACCESS_TOKEN_USER_KEY, googleIdentity);
    AccessToken accessToken = AbstractProvider.getInstance().getAccessToken(googleIdentity, scopes);
    return AccessBoundaryUtils.addAccessBoundary(accessToken, target);
  }

  @Override
  protected Object fromJson(String json) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(json, AccessToken.class);
  }
}
