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

package com.google.cloud.broker.client.utils;

import com.google.cloud.broker.apps.brokerserver.protobuf.BrokerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

public class GrpcUtils {

  public static ManagedChannel newManagedChannel(
      String brokerHostname, int brokerPort, boolean useTLS, String tlsCertificate) {
    // Create the gRPC stub
    NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(brokerHostname, brokerPort);
    if (!useTLS) {
      channelBuilder.usePlaintext();
    } else {
      SslContextBuilder sslContextBuilder;
      if (tlsCertificate == null || tlsCertificate.equals("")) {
        sslContextBuilder = GrpcSslContexts.forClient();
      } else {
        // A certificate is provided, so add it to the stub's build
        InputStream inputStream = new ByteArrayInputStream(tlsCertificate.getBytes());
        try {
          sslContextBuilder = GrpcSslContexts.forClient().trustManager(inputStream);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "The provided certificate for the broker service is invalid");
        }
      }
      try {
        SslContext sslContext = sslContextBuilder.build();
        channelBuilder.sslContext(sslContext);
      } catch (SSLException e) {
        throw new RuntimeException(e);
      }
    }
    return channelBuilder.executor(Executors.newSingleThreadExecutor()).build();
  }

  public static BrokerGrpc.BrokerBlockingStub newStub(ManagedChannel managedChannel) {
    int DEADLINE_MILLISECONDS = 20 * 1000; // Timeout for RPC calls
    return BrokerGrpc.newBlockingStub(managedChannel)
        .withDeadlineAfter(DEADLINE_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}
