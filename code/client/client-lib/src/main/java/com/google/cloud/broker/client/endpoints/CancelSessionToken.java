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

package com.google.cloud.broker.client.endpoints;

import com.google.cloud.broker.apps.brokerserver.protobuf.CancelSessionTokenRequest;
import com.google.cloud.broker.client.connect.BrokerGateway;
import com.google.cloud.broker.client.connect.BrokerServerInfo;

public class CancelSessionToken {

  public static void submit(BrokerServerInfo serverInfo, String sessionToken) {
    BrokerGateway gateway = new BrokerGateway(serverInfo);
    gateway.setSPNEGOToken();
    CancelSessionTokenRequest request =
        CancelSessionTokenRequest.newBuilder().setSessionToken(sessionToken).build();
    gateway.getStub().cancelSessionToken(request);
    gateway.getManagedChannel().shutdown();
  }
}
