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

import static com.google.cloud.broker.client.hadoop.fs.TestingTools.*;
import static org.junit.Assert.assertEquals;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.cloud.broker.apps.brokerserver.protobuf.*;
import com.google.cloud.broker.authentication.backends.FakeKDC;
import com.google.cloud.broker.client.utils.GrpcUtils;
import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem;
import com.google.cloud.hadoop.util.AccessTokenProvider.AccessToken;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import javax.security.auth.Subject;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.LoggerFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
  "com.sun.org.apache.xerces.*",
  "javax.xml.*",
  "javax.activation.*",
  "org.xml.*",
  "org.w3c.*",
  "javax.crypto.*",
  "javax.net.ssl.*",
  "javax.security.*",
  "org.ietf.jgss.*"
})
@PrepareForTest({GrpcUtils.class}) // Classes to be mocked
public class BrokerAccessTokenProviderTest {

  private static FakeKDC fakeKDC;

  @Rule public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @BeforeClass
  public static void setUpClass() {
    fakeKDC = new FakeKDC(REALM);
    fakeKDC.start();
    fakeKDC.createPrincipal(ALICE);
    fakeKDC.createPrincipal(BROKER);
    TestingTools.initHadoop();
  }

  @AfterClass
  public static void tearDownClass() {
    if (fakeKDC != null) {
      fakeKDC.stop();
    }
  }

  public AccessToken refresh(Configuration conf) {
    BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
    Text service = new Text(MOCK_BUCKET);
    binding.bindToFileSystem(new GoogleHadoopFileSystem(), service);
    BrokerAccessTokenProvider provider;
    try {
      provider = (BrokerAccessTokenProvider) binding.deployUnbonded();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    provider.setConf(conf);
    provider.refresh();
    return provider.getAccessToken();
  }

  private static class FakeServer extends TestingTools.FakeBrokerImpl {

    FakeServer(FakeKDC fakeKDC) {
      super(fakeKDC);
    }

    @Override
    public void getAccessToken(
        GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
      try {
        String authenticatedUser = authenticateUser();
        GetAccessTokenResponse response =
            GetAccessTokenResponse.newBuilder()
                .setAccessToken(
                    "FakeAccessToken/AuthenticatedUser="
                        + authenticatedUser
                        + ";Owner="
                        + request.getOwner()
                        + ";Target="
                        + request.getTarget())
                .setExpiresAt(11111111111111L)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
      } catch (StatusRuntimeException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Test
  public void testProviderRefreshWhileNotLoggedIn() {
    ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();
    Configuration conf = TestingTools.getBrokerConfig();
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(BrokerAccessTokenProvider.class)).addAppender(logWatcher);
    refresh(conf);
    int logSize = logWatcher.list.size();
    assertEquals(
        logWatcher.list.get(logSize - 1).getFormattedMessage(),
        "Not logged-in with Kerberos, so defaulting to Google application default credentials");
    ((Logger) LoggerFactory.getLogger(BrokerAccessTokenProvider.class)).detachAndStopAllAppenders();
  }

  @Test
  public void testProviderRefresh() throws IOException {
    TestingTools.startServer(new FakeServer(fakeKDC), grpcCleanup);
    Configuration conf = TestingTools.getBrokerConfig();
    Subject alice = fakeKDC.login(ALICE);
    UserGroupInformation.loginUserFromSubject(alice);
    AccessToken token = refresh(conf);
    assertEquals(
        "FakeAccessToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=" + MOCK_BUCKET,
        token.getToken());
    UserGroupInformation.setLoginUser(null);
  }

  /** Same as testProviderRefresh but with access boundary disabled */
  @Test
  public void testProviderRefreshWithoutAccessBoundary() throws IOException {
    TestingTools.startServer(new FakeServer(fakeKDC), grpcCleanup);
    Configuration conf = TestingTools.getBrokerConfig();
    conf.set("gcp.token.broker.access.boundary.enabled", "false");
    Subject alice = fakeKDC.login(ALICE);
    UserGroupInformation.loginUserFromSubject(alice);
    AccessToken token = refresh(conf);
    assertEquals(
        "FakeAccessToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=",
        token.getToken());
    UserGroupInformation.setLoginUser(null);
  }
}
