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

package com.google.cloud.broker.apps.brokerserver.validation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Member;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import io.grpc.Status;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.apps.brokerserver.accesstokens.providers.DomainWideDelegationAuthorityProvider;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.usermapping.AbstractUserMapper;
import com.google.cloud.broker.utils.Constants;

public class ProxyUserValidation {

    private final static String CONFIG_PROXY = "proxy";
    private final static String CONFIG_GROUPS = "groups";
    private final static String CONFIG_USERS = "users";

    private static boolean isWhitelistedByUsername(Config proxyConfig, String impersonated) {
        // Check if user is whitelisted directly by name
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

    private static boolean isWhitelistedByGroupMembership(Config proxyConfig, String impersonated) {
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
            String googleIdentity = AbstractUserMapper.getInstance().map(impersonated);
            Directory directory = getDirectoryService();
            for (String proxyableGroup : proxyableGroups) {
                try {
                    List<Member> members = directory.members().list(proxyableGroup).execute().getMembers();
                    for (Member member : members) {
                        if (member.getEmail().equals(googleIdentity)) {
                            // User is member of whitelisted group
                            return true;
                        }
                    }
                }
                catch (GoogleJsonResponseException e) {
                    // Continue
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public static void validateImpersonator(String impersonator, String impersonated) {
        if (impersonator.equals(impersonated)) {
            // A user is allowed to impersonate themselves
            return;
        }

        List<? extends Config> proxyConfigs = AppSettings.getInstance().getConfigList(AppSettings.PROXY_USERS);
        for (Config proxyConfig : proxyConfigs) {
            String proxy = proxyConfig.getString(CONFIG_PROXY);
            if (impersonator.equals(proxy)) {
                if (isWhitelistedByUsername(proxyConfig, impersonated)) {
                    // The user is directly whitelisted by its username
                    return;
                }
                else if (isWhitelistedByGroupMembership(proxyConfig, impersonated)) {
                    // The user is whitelisted by group membership
                    return;
                }
            }
        }

        throw Status.PERMISSION_DENIED
            .withDescription(String.format("%s is not a whitelisted impersonator for %s", impersonator, impersonated))
            .asRuntimeException();
    }

    public static Directory getDirectoryService() {
        DomainWideDelegationAuthorityProvider provider = new DomainWideDelegationAuthorityProvider();
        AccessToken accessToken = provider.getAccessToken(
            AppSettings.getInstance().getString(AppSettings.GSUITE_ADMIN),
            List.of(DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER_READONLY));
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(accessToken.getValue());
        return new Directory.Builder(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), credential)
            .setApplicationName(Constants.APPLICATION_NAME).build();
    }

}