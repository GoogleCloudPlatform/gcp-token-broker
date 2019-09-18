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

package com.google.cloud.broker.hadoop.fs;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import com.google.cloud.broker.protobuf.BrokerGrpc;

public class GrpcUtils {

    // Timeout for RPC calls
    private static int DEADLINE_MILLISECONDS = 20*1000;

    public static ManagedChannel newManagedChannel(String brokerHostname, int brokerPort, boolean useTLS, String tlsCertificate) {
        // Create the gRPC stub
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(brokerHostname, brokerPort);
        if (!useTLS) {
            builder = builder.usePlaintext();
        }
        else if (!tlsCertificate.equals("")) {
            // A certificate is provided, so add it to the stub's build
            InputStream inputStream = new ByteArrayInputStream(tlsCertificate.getBytes());
            try {
                builder = builder.sslContext(GrpcSslContexts.forClient()
                    .trustManager(inputStream).build());
            } catch (SSLException e) {
                throw new RuntimeException(e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The provided certificate for the broker service is invalid");
            }
        }
        ManagedChannel managedChannel = builder
            .executor(Executors.newSingleThreadExecutor())
            .build();
        return managedChannel;
    }

    public static BrokerGrpc.BrokerBlockingStub newStub(ManagedChannel managedChannel) {
        return BrokerGrpc.newBlockingStub(managedChannel)
            .withDeadlineAfter(DEADLINE_MILLISECONDS, TimeUnit.MILLISECONDS);
    }


}
