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

import javax.security.auth.Subject;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Base64;

import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import static com.google.cloud.broker.hadoop.fs.SpnegoUtilsTest.TGT_ERROR;
import com.google.cloud.hadoop.util.AccessTokenProvider.AccessToken;

import com.google.cloud.broker.protobuf.*;

@RunWith(PowerMockRunner.class)

@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*", "javax.crypto.*", "javax.net.ssl.*", "javax.security.*", "org.ietf.jgss.*"})
@PrepareForTest({GrpcUtils.class})  // Classes to be mocked
public class BrokerAccessTokenProviderTest {

    private static final String REALM = "EXAMPLE.COM";
    private static final String BROKER_HOST = "testhost";
    private static final String BROKER_NAME = "broker";
    private static final String MOCK_BUCKET = "gs://example";
    private static final String ALICE = "alice@EXAMPLE.COM";

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    public AccessToken refresh(Configuration conf) throws IOException {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        Text service = new Text(MOCK_BUCKET);
        binding.bindToFileSystem(new GoogleHadoopFileSystem(), service);
        BrokerAccessTokenProvider provider = (BrokerAccessTokenProvider) binding.deployUnbonded();
        provider.setConf(conf);
        provider.refresh();
        return provider.getAccessToken();
    }

    @Test
    public void testProviderRefreshWhileNotLoggedIn() {
        try {
            Configuration conf = new Configuration();
            refresh(conf);
            fail();
        } catch (Exception e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertEquals(
                "User is not logged-in with Kerberos or cannot authenticate with the broker. Kerberos error message: " + TGT_ERROR,
                e.getMessage()
            );
        }
    }

    @Test
    public void testProviderRefresh() {
        String serverName = startServer();
        ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
        BrokerGrpc.BrokerBlockingStub stub = BrokerGrpc.newBlockingStub(channel);

        mockStatic(GrpcUtils.class);
        when(GrpcUtils.newManagedChannel(BROKER_HOST, 1234, false, "")).thenReturn(channel);
        when(GrpcUtils.newStub(channel)).thenReturn(stub);

        try {
            Configuration conf = new Configuration();
            conf.set("gcp.token.broker.uri.hostname", BROKER_HOST);
            conf.set("gcp.token.broker.uri.port", "1234");
            conf.set("gcp.token.broker.servicename", BROKER_NAME);
            conf.set("gcp.token.broker.realm", REALM);
            conf.set("gcp.token.broker.tls.enabled", "false");
            conf.set("hadoop.security.authentication", "kerberos");

            UserGroupInformation.setConfiguration(conf);
            UserGroupInformation.loginUserFromKeytab(ALICE,
                "/etc/security/keytabs/users/alice.keytab");

            AccessToken token;
            try {
                token = refresh(conf);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertEquals("FakeAccessToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=" + MOCK_BUCKET, token.getToken());

        } catch (Exception e) {throw new RuntimeException(e);}

    }

    // Fake server for testing -----------------------------------------------------------------------------------

    private String startServer() {
        String serverName = InProcessServerBuilder.generateName();
        try {
            grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(ServerInterceptors.intercept(new FakeBrokerImpl(), new AuthorizationHeaderServerInterceptor()))
                .build().start());
            return serverName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class FakeBrokerImpl extends BrokerGrpc.BrokerImplBase {

        private String authenticateUser () {
            String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();
            String spnegoToken = authorizationHeader.split("\\s")[1];

            // Let the broker decrypt the token and verify the user's identity
            Subject broker = SpnegoUtilsTest.login("broker");
            return Subject.doAs(broker, (PrivilegedAction<String>) () ->
                SpnegoUtilsTest.decryptToken(Base64.getDecoder().decode(spnegoToken.getBytes())
            ));
        }

        @Override
        public void getAccessToken(GetAccessTokenRequest request, StreamObserver<GetAccessTokenResponse> responseObserver) {
            try {
                String authenticatedUser = authenticateUser();
                GetAccessTokenResponse response = GetAccessTokenResponse.newBuilder()
                    .setAccessToken("FakeAccessToken/AuthenticatedUser=" + authenticatedUser + ";Owner=" + request.getOwner() + ";Target=" + request.getTarget())
                    .setExpiresAt(11111111111111L)
                    .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
            catch (StatusRuntimeException e) {throw new RuntimeException(e);}
        }

    }
}
