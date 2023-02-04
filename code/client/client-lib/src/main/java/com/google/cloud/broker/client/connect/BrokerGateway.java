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

package com.google.cloud.broker.client.connect;

import com.google.cloud.broker.apps.brokerserver.protobuf.BrokerGrpc;
import com.google.cloud.broker.client.utils.GrpcUtils;
import com.google.cloud.broker.client.utils.OAuthUtils;
import com.google.cloud.broker.client.utils.SpnegoUtils;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.ietf.jgss.GSSException;

public class BrokerGateway {

  public static final Metadata.Key<String> GCP_AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
  public static final Metadata.Key<String> BROKER_AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("broker-authorization", Metadata.ASCII_STRING_MARSHALLER);
  public static String SESSION_AUTH_HEADER = "BrokerSession";
  public static String NEGOTIATE_AUTH_HEADER = "Negotiate";
  public static String BEARER_AUTH_HEADER = "Bearer";
  private BrokerGrpc.BrokerBlockingStub stub;
  private ManagedChannel managedChannel;
  private BrokerServerInfo serverInfo;

  public BrokerGateway(BrokerServerInfo serverInfo) {
    this.serverInfo = serverInfo;

    // Extract the host and port from the URI
    URL url;
    try {
      url = new URL(serverInfo.getServerUri());
    } catch (MalformedURLException e) {
      throw new RuntimeException("Invalid server URI: " + serverInfo.getServerUri());
    }
    String host = url.getHost();
    int port = url.getPort();

    // Determine if TLS should be used
    boolean useTLS;
    String protocol = url.getProtocol();
    if (protocol.equals("http")) {
      useTLS = false;
      if (port == -1) {
        // Default HTTP port
        port = 80;
      }
    } else if (protocol.equals("https")) {
      useTLS = true;
      if (port == -1) {
        // Default HTTPS port
        port = 443;
      }
    } else {
      throw new RuntimeException(
          "Incorrect URI scheme `" + protocol + " ` in server URI: " + serverInfo.getServerUri());
    }

    String tlsCertificate = serverInfo.getCertificate();
    if (tlsCertificate == null) {
      String tlsCerfiticatePath = serverInfo.getCertificatePath();
      if (tlsCerfiticatePath != null) {
        try {
          List<String> lines =
              Files.readAllLines(Paths.get(tlsCerfiticatePath), StandardCharsets.UTF_8);
          tlsCertificate = Joiner.on("\n").join(lines);
        } catch (IOException e) {
          throw new RuntimeException("Error reading the TLS certificate file: " + e.getMessage());
        }
      }
    }

    managedChannel = GrpcUtils.newManagedChannel(host, port, useTLS, tlsCertificate);
    stub = GrpcUtils.newStub(managedChannel);
  }

  public BrokerGrpc.BrokerBlockingStub getStub() {
    return stub;
  }

  public ManagedChannel getManagedChannel() {
    return managedChannel;
  }

  public void setSPNEGOToken() {
    String encodedToken;
    try {
      encodedToken =
          BaseEncoding.base64()
              .encode(SpnegoUtils.newSPNEGOToken(serverInfo.getKerberosPrincipal()));
    } catch (GSSException e) {
      // Clean up the channel before re-throwing the exception
      managedChannel.shutdownNow();
      throw new RuntimeException(
          "Failed creating a SPNEGO token. Make sure that you have run kinit and that your Kerberos configuration is correct. See the full Kerberos error message: "
              + e.getMessage());
    }
    // Get the Google IDToken, which is necessary to authenticate with the Google load balancer
    String idToken =
        OAuthUtils.getApplicationDefaultIdToken(serverInfo.getServerUri()).getTokenValue();
    // Set authorization headers
    Metadata metadata = new Metadata();
    metadata.put(GCP_AUTHORIZATION_METADATA_KEY, BEARER_AUTH_HEADER + " " + idToken);
    metadata.put(BROKER_AUTHORIZATION_METADATA_KEY, NEGOTIATE_AUTH_HEADER + " " + encodedToken);
    stub = MetadataUtils.attachHeaders(stub, metadata);
  }

  public void setSessionToken(String sessionToken) {
    // Get the Google IDToken, which is necessary to authenticate with the Google load balancer
    String idToken =
        OAuthUtils.getApplicationDefaultIdToken(serverInfo.getServerUri()).getTokenValue();
    // Set authorization headers
    Metadata metadata = new Metadata();
    metadata.put(GCP_AUTHORIZATION_METADATA_KEY, BEARER_AUTH_HEADER + " " + idToken);
    metadata.put(BROKER_AUTHORIZATION_METADATA_KEY, SESSION_AUTH_HEADER + " " + sessionToken);
    stub = MetadataUtils.attachHeaders(stub, metadata);
  }
}
