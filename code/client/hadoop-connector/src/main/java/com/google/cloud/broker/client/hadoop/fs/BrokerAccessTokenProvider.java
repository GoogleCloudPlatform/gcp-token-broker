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

package com.google.cloud.broker.client.hadoop.fs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.broker.client.connect.BrokerServerInfo;
import com.google.cloud.broker.client.credentials.BrokerBaseCredentials;
import com.google.cloud.broker.client.credentials.BrokerKerberosCredentials;
import com.google.cloud.broker.client.credentials.BrokerSessionCredentials;
import com.google.cloud.broker.client.utils.OAuthUtils;
import com.google.cloud.hadoop.util.AccessTokenProvider;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.LoggerFactory;

public final class BrokerAccessTokenProvider implements AccessTokenProvider {

  public static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";
  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Configuration config;
  private List<String> appDefaultCredsUsers;
  private AccessToken accessToken;
  private BrokerTokenIdentifier tokenIdentifier;
  private Text service;
  private static final AccessToken EXPIRED_TOKEN = new AccessToken("", -1L);

  public BrokerAccessTokenProvider(Text service) {
    this(service, null);
  }

  public BrokerAccessTokenProvider(Text service, BrokerTokenIdentifier bti) {
    this.service = service;
    this.tokenIdentifier = bti;
    this.accessToken = EXPIRED_TOKEN;
  }

  @Override
  public AccessToken getAccessToken() {
    return accessToken;
  }

  private void refreshCredentialsFromApplicationDefault() {
    GoogleCredentials applicationDefaultCredentials =
        OAuthUtils.getApplicationDefaultCredentials().createScoped(CLOUD_PLATFORM_SCOPE);
    try {
      applicationDefaultCredentials.refresh();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    com.google.auth.oauth2.AccessToken token = applicationDefaultCredentials.getAccessToken();
    accessToken = new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
  }

  @Override
  public void refresh() {
    // Retrieve the current and login users from the ambient context
    UserGroupInformation currentUser;
    UserGroupInformation loginUser;
    try {
      currentUser = UserGroupInformation.getCurrentUser();
      loginUser = UserGroupInformation.getLoginUser();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Instantiate the proper credentials class based on the type of authentication (direct or
    // delegated)
    BrokerServerInfo serverInfo = Utils.getBrokerDetailsFromConfig(config);
    BrokerBaseCredentials credentials;
    if (tokenIdentifier == null) { // Using direct authentication
      if (!loginUser.hasKerberosCredentials()) {
        logger.info(
            "Not logged-in with Kerberos, so defaulting to Google application default credentials");
        refreshCredentialsFromApplicationDefault();
        return;
      }
      if (appDefaultCredsUsers.contains(loginUser.getUserName())) {
        // Experimental feature: Let some users systematically use the default credentials.
        // This is useful for Dataproc as some users (e.g. "mapred" for the job history
        // server) need to access GCS directly.
        logger.info(
            String.format(
                "Using Google application default credentials for %s", loginUser.getUserName()));
        refreshCredentialsFromApplicationDefault();
        return;
      }
      credentials =
          new BrokerKerberosCredentials(
              serverInfo,
              currentUser.getUserName(),
              Collections.singleton(BrokerTokenIdentifier.GCS_SCOPE),
              Utils.getTarget(config, service));
    } else { // Using delegated authentication
      credentials = new BrokerSessionCredentials(serverInfo, tokenIdentifier.getSessionToken());
    }

    // Generate the access token
    loginUser.doAs(
        (PrivilegedAction<Void>)
            () -> {
              try {
                credentials.refresh();
              } catch (Exception e) {
                throw new RuntimeException(
                    String.format(
                        "Failed refreshing credentials for currentUser=[`%s`, hasKerberosCredentials=`%s`], loginUser=[`%s`, hasKerberosCredentials=`%s`], service=`%s`",
                        currentUser,
                        currentUser.hasKerberosCredentials(),
                        loginUser,
                        loginUser.hasKerberosCredentials(),
                        service),
                    e);
              }
              return null;
            });
    com.google.auth.oauth2.AccessToken token = credentials.getAccessToken();
    accessToken = new AccessToken(token.getTokenValue(), token.getExpirationTime().getTime());
  }

  @Override
  public void setConf(Configuration config) {
    this.config = config;
    this.appDefaultCredsUsers =
        Stream.of(config.get(Utils.CONFIG_USE_APP_DEFAULT_CREDENTIALS, "").split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
  }

  @Override
  public Configuration getConf() {
    return config;
  }
}
