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
import com.google.cloud.broker.client.hadoop.fs.Utils;
import com.google.common.base.Joiner;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;

public class CommandUtils {

  private static final Pattern gcsUriPattern = Pattern.compile("gs://([^/]*)(.*)?");

  public static String readSessionToken(String file) {
    Path path = Paths.get(file);
    try {
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      return Joiner.on("\n").join(lines);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static BrokerTokenIdentifier getBTI(String sessionToken) {
    UserGroupInformation loginUser;
    try {
      loginUser = UserGroupInformation.getLoginUser();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Text username = new Text(loginUser.getUserName());
    BrokerTokenIdentifier identifier = new BrokerTokenIdentifier();
    identifier.setOwner(username);
    identifier.setRenewer(username);
    identifier.setRealUser(username);
    identifier.setSessionToken(sessionToken);
    return identifier;
  }

  public static Token<BrokerTokenIdentifier> getTokenBTI(String sessionToken, Text service) {
    BrokerTokenIdentifier identifier = CommandUtils.getBTI(sessionToken);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(byteArrayOutputStream);
    try {
      identifier.write(data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new Token<>(
        byteArrayOutputStream.toByteArray(), new byte[0], BrokerTokenIdentifier.KIND, service);
  }

  /** Retrieves the bucket name from a fully-qualified GCS URI. */
  public static String extractBucketNameFromGcsUri(String gcsURI) {
    Matcher m = gcsUriPattern.matcher(gcsURI);
    if (m.find()) {
      return m.group(1);
    } else {
      throw new RuntimeException("Incorrect GCS URI: " + gcsURI);
    }
  }

  public static void showConfig(Configuration config) {
    UserGroupInformation loginUser;
    try {
      loginUser = UserGroupInformation.getLoginUser();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    System.out.println("> Current configuration:\n");
    System.out.println(String.format("* Authenticated user: %s", loginUser));
    String[] configKeys =
        new String[] {
          Utils.CONFIG_URI,
          Utils.CONFIG_PRINCIPAL,
          Utils.CONFIG_CERTIFICATE,
          Utils.CONFIG_CERTIFICATE_PATH,
          Utils.CONFIG_ACCESS_BOUNDARY_ENABLED
        };
    for (String configKey : configKeys) {
      System.out.println(String.format("* %s: %s", configKey, config.get(configKey)));
    }
  }
}
