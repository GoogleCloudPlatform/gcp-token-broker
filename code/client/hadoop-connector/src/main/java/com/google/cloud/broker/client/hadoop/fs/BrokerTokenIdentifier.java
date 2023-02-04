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

import com.google.cloud.broker.client.connect.BrokerServerInfo;
import com.google.cloud.broker.client.endpoints.GetSessionToken;
import com.google.cloud.hadoop.fs.gcs.auth.DelegationTokenIOException;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Collections;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.delegation.web.DelegationTokenIdentifier;

public class BrokerTokenIdentifier extends DelegationTokenIdentifier {

  public static final Text KIND = new Text("GCPBrokerSessionToken");
  static final String GCS_SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
  private String sessionToken;

  public BrokerTokenIdentifier() {
    super(KIND);
  }

  public BrokerTokenIdentifier(
      Configuration config, Text owner, Text renewer, Text realUser, Text service) {
    super(KIND, owner, renewer, realUser);
    UserGroupInformation currentUser;
    UserGroupInformation loginUser;
    try {
      currentUser = UserGroupInformation.getCurrentUser();
      loginUser = UserGroupInformation.getLoginUser();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BrokerServerInfo serverInfo = Utils.getBrokerDetailsFromConfig(config);
    setSessionToken(
        loginUser.doAs(
            (PrivilegedAction<String>)
                () -> {
                  return GetSessionToken.submit(
                      serverInfo,
                      currentUser.getUserName(),
                      renewer.toString(),
                      Collections.singleton(GCS_SCOPE),
                      Utils.getTarget(config, service));
                }));
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    Text.writeString(out, sessionToken);
  }

  @Override
  public void readFields(DataInput in) throws DelegationTokenIOException, IOException {
    super.readFields(in);
    this.sessionToken = Text.readString(in, 32 * 1024);
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public String getSessionToken() {
    return sessionToken;
  }
}
