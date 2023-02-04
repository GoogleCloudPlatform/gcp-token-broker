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

package com.google.cloud.broker.client.hadoop.fs.commands;

import com.google.cloud.broker.client.hadoop.fs.BrokerAccessTokenProvider;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "GetAccessToken", description = "Retrieves an Oauth2 access token")
public class GetAccessToken implements Runnable {

  @Option(
      names = {"-u", "--uri"},
      required = true,
      description = "GCS URI for the access token")
  private String uri;

  @Option(
      names = {"-i", "--impersonate"},
      description = "(Optional) Impersonated user")
  private String impersonate;

  @Option(
      names = {"-f", "--session-token-file"},
      description = "File that contains a session token for delegated authentication")
  private String file;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display this help")
  boolean help;

  private void sendRequest(Configuration config, String sessionToken) {
    String bucket = CommandUtils.extractBucketNameFromGcsUri(uri);
    Text service = new Text(bucket);
    BrokerAccessTokenProvider provider;
    if (sessionToken == null) {
      // Direct authentication
      provider = new BrokerAccessTokenProvider(service);
    } else {
      // Delegated authentication
      provider = new BrokerAccessTokenProvider(service, CommandUtils.getBTI(sessionToken));
    }
    provider.setConf(config);
    provider.refresh();
    System.out.println("\n> Access token:\n");
    System.out.println(provider.getAccessToken().getToken());
  }

  @Override
  public void run() {
    Configuration config = new Configuration();
    CommandUtils.showConfig(config);
    if (impersonate == null) {
      if (file == null) {
        // Using direct authentication
        sendRequest(config, null);
      } else {
        // Using a session token for delegated authentication
        String sessionToken = CommandUtils.readSessionToken(file);
        sendRequest(config, sessionToken);
      }
    } else {
      if (file != null) {
        throw new RuntimeException(
            "You cannot provide both a session token and an impersonated user");
      }
      // Impersonation via a proxy user
      try {
        UserGroupInformation ugi =
            UserGroupInformation.createProxyUser(impersonate, UserGroupInformation.getLoginUser());
        ugi.doAs(
            (PrivilegedExceptionAction<Void>)
                () -> {
                  sendRequest(config, null);
                  return null;
                });
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void main(String[] args) {
    CommandLine.run(new GetAccessToken(), args);
  }
}
