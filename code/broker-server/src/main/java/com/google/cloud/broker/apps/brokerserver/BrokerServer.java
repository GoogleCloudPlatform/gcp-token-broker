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

package com.google.cloud.broker.apps.brokerserver;

import com.google.cloud.broker.apps.brokerserver.endpoints.CancelSessionToken;
import com.google.cloud.broker.apps.brokerserver.endpoints.GetAccessToken;
import com.google.cloud.broker.apps.brokerserver.endpoints.GetSessionToken;
import com.google.cloud.broker.apps.brokerserver.endpoints.RenewSessionToken;
import com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils;
import com.google.cloud.broker.apps.brokerserver.protobuf.*;
import com.google.cloud.broker.authentication.AuthorizationHeaderServerInterceptor;
import com.google.cloud.broker.checks.SystemCheck;
import com.google.cloud.broker.secretmanager.SecretManager;
import com.google.cloud.broker.settings.AppSettings;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.stub.StreamObserver;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

public class BrokerServer {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Server server;

  private final String host;
  private final int port;
  private final boolean tlsEnabled;
  private static ServerServiceDefinition serviceDefinition =
      ServerInterceptors.intercept(
          new BrokerImpl(),
          new AuthorizationHeaderServerInterceptor(),
          new ClientAddressServerInterceptor());

  private BrokerServer() {
    this.host = AppSettings.getInstance().getString(AppSettings.SERVER_HOST);
    this.port = AppSettings.getInstance().getInt(AppSettings.SERVER_PORT);
    this.tlsEnabled = AppSettings.getInstance().getBoolean(AppSettings.TLS_ENABLED);
  }

  private SslContextBuilder getSslContextBuilder() {
    String certChainFilePath =
        AppSettings.getInstance().getString(AppSettings.TLS_CERTIFICATE_PATH);
    String privateKeyFilePath =
        AppSettings.getInstance().getString(AppSettings.TLS_PRIVATE_KEY_PATH);
    SslContextBuilder sslClientContextBuilder =
        SslContextBuilder.forServer(new File(certChainFilePath), new File(privateKeyFilePath));
    return GrpcSslContexts.configure(sslClientContextBuilder, SslProvider.OPENSSL);
  }

  static ServerServiceDefinition getServiceDefinition() {
    return serviceDefinition;
  }

  private void start() throws IOException {
    NettyServerBuilder builder =
        NettyServerBuilder.forAddress(new InetSocketAddress(host, port))
            .addService(serviceDefinition);
    if (tlsEnabled) {
      builder.sslContext(getSslContextBuilder().build());
    }
    server = builder.build().start();
    logger.info("Server listening on " + port + "...");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                BrokerServer.this.stop();
              }
            });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private static void setLoggingLevel() {
    Level level = Level.parse(AppSettings.getInstance().getString(AppSettings.LOGGING_LEVEL));
    Logger root = Logger.getLogger("");
    root.setLevel(level);
    for (Handler handler : root.getHandlers()) {
      handler.setLevel(level);
    }
  }

  static class BrokerImpl extends BrokerGrpc.BrokerImplBase {

    @Override
    public void getAccessToken(
        GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
      try {
        GetAccessToken.run(request, responseObserver);
      } catch (StatusRuntimeException e) {
        LoggingUtils.errorAuditLog(e);
        responseObserver.onError(e);
      }
    }

    @Override
    public void getSessionToken(
        GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
      try {
        GetSessionToken.run(request, responseObserver);
      } catch (StatusRuntimeException e) {
        LoggingUtils.errorAuditLog(e);
        responseObserver.onError(e);
      }
    }

    @Override
    public void renewSessionToken(
        RenewSessionTokenRequest request,
        StreamObserver<RenewSessionTokenResponse> responseObserver) {
      try {
        RenewSessionToken.run(request, responseObserver);
      } catch (StatusRuntimeException e) {
        LoggingUtils.errorAuditLog(e);
        responseObserver.onError(e);
      }
    }

    @Override
    public void cancelSessionToken(
        CancelSessionTokenRequest request,
        StreamObserver<CancelSessionTokenResponse> responseObserver) {
      try {
        CancelSessionToken.run(request, responseObserver);
      } catch (StatusRuntimeException e) {
        LoggingUtils.errorAuditLog(e);
        responseObserver.onError(e);
      }
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    setLoggingLevel();
    SecretManager.downloadSecrets();
    final BrokerServer server = new BrokerServer();
    server.start();

    if (AppSettings.getInstance().getBoolean(AppSettings.SYSTEM_CHECK_ENABLED)) {
      SystemCheck.runChecks();
    }

    server.blockUntilShutdown();
  }
}
