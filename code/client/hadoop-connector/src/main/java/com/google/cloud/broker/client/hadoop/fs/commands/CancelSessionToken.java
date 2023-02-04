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

import com.google.cloud.broker.client.hadoop.fs.BrokerTokenIdentifier;
import com.google.cloud.broker.client.hadoop.fs.BrokerTokenRenewer;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.token.Token;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "CancelSessionToken", description = "Retrieves an Oauth2 access token")
public class CancelSessionToken implements Runnable {

  @Option(
      names = {"-u", "--uri"},
      required = true,
      description = "GCS URI for the access token")
  private String uri;

  @Option(
      names = {"-f", "--session-token-file"},
      required = true,
      description = "File that contains the session token to be canceled")
  private String file;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display this help")
  boolean help;

  private void sendRequest(Configuration config, String sessionToken) {
    String bucket = CommandUtils.extractBucketNameFromGcsUri(uri);
    Text service = new Text(bucket);
    Token<BrokerTokenIdentifier> token = CommandUtils.getTokenBTI(sessionToken, service);
    BrokerTokenRenewer renewer = new BrokerTokenRenewer();
    try {
      renewer.cancel(token, config);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    System.out.println("\n> Session token successfully canceled\n");
  }

  @Override
  public void run() {
    Configuration config = new Configuration();
    CommandUtils.showConfig(config);
    String sessionToken = CommandUtils.readSessionToken(file);
    sendRequest(config, sessionToken);
  }

  public static void main(String[] args) {
    CommandLine.run(new CancelSessionToken(), args);
  }
}
