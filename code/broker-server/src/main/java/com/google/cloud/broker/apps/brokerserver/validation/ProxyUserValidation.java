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

package com.google.cloud.broker.apps.brokerserver.validation;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Member;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.DomainWideDelegationAuthorityProvider;
import com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.usermapping.AbstractUserMapper;
import com.google.cloud.broker.utils.Constants;
import com.google.cloud.broker.validation.EmailValidation;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import io.grpc.Status;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ProxyUserValidation {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String CONFIG_PROXY = "proxy";
  private static final String CONFIG_GROUPS = "groups";
  private static final String CONFIG_USERS = "users";

  private static boolean isAllowlistedByUsername(Config proxyConfig, String impersonated) {
    // Check if user is allowlisted directly by name
    List<String> proxyableUsers;
    try {
      proxyableUsers = proxyConfig.getStringList(CONFIG_USERS);
    } catch (ConfigException.Missing e) {
      // No users setting specified
      return false;
    }

    if (proxyableUsers.contains("*")) {
      // Any users can be impersonated
      return true;
    }

    return proxyableUsers.contains(impersonated);
  }

  private static boolean isAllowlistedByGroupMembership(Config proxyConfig, String impersonated) {
    List<String> proxyableGroups;
    try {
      proxyableGroups = proxyConfig.getStringList(CONFIG_GROUPS);
    } catch (ConfigException.Missing e) {
      // No groups setting specified
      return false;
    }

    if (proxyableGroups.contains("*")) {
      // Any users from any groups can be impersonated
      return true;
    }

    try {
      Directory directory = getDirectoryService();
      for (String proxyableGroup : proxyableGroups) {
        try {
          List<Member> members = directory.members().list(proxyableGroup).execute().getMembers();
          for (Member member : members) {
            if (member.getEmail().equals(impersonated)) {
              // User is member of allowlisted group
              return true;
            }
          }
        } catch (GoogleJsonResponseException e) {
          // Continue
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  public static void validateImpersonator(String impersonator, String impersonated) {
    String mappedImpersonated;
    try {
      mappedImpersonated = AbstractUserMapper.getInstance().map(impersonated);
    } catch (IllegalArgumentException e) {
      logger.error(e.getMessage());
      mappedImpersonated = null;
    }
    if (mappedImpersonated != null) {
      EmailValidation.validateEmail(mappedImpersonated);
      MDC.put(LoggingUtils.MDC_AUTH_MODE_PROXY_IMPERSONATED_USER_KEY, impersonated);
      List<? extends Config> proxyConfigs =
          AppSettings.getInstance().getConfigList(AppSettings.PROXY_USERS);
      for (Config proxyConfig : proxyConfigs) {
        String proxy = proxyConfig.getString(CONFIG_PROXY);
        if (impersonator.equals(proxy)) {
          if (isAllowlistedByUsername(proxyConfig, mappedImpersonated)) {
            // The user is directly allowlisted by its username
            return;
          } else if (isAllowlistedByGroupMembership(proxyConfig, mappedImpersonated)) {
            // The user is allowlisted by group membership
            return;
          }
        }
      }
    }
    throw Status.PERMISSION_DENIED
        .withDescription(
            "Impersonation of `" + impersonated + "` by `" + impersonator + "` is not allowed")
        .asRuntimeException();
  }

  public static Directory getDirectoryService() {
    DomainWideDelegationAuthorityProvider provider = new DomainWideDelegationAuthorityProvider();
    AccessToken accessToken =
        provider.getAccessToken(
            AppSettings.getInstance().getString(AppSettings.GSUITE_ADMIN),
            List.of(DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER_READONLY));
    Credential credential =
        new Credential(BearerToken.authorizationHeaderAccessMethod())
            .setAccessToken(accessToken.getValue());
    return new Directory.Builder(
            Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), credential)
        .setApplicationName(Constants.APPLICATION_NAME)
        .build();
  }
}
