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

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem;

import static com.google.cloud.broker.hadoop.fs.SpnegoUtilsTest.TGT_ERROR;
import static com.google.cloud.broker.hadoop.fs.TestingTools.*;
import com.google.cloud.hadoop.util.AccessTokenProvider.AccessToken;
import com.google.cloud.broker.protobuf.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*", "javax.crypto.*", "javax.net.ssl.*", "javax.security.*", "org.ietf.jgss.*"})
@PrepareForTest({GrpcUtils.class})  // Classes to be mocked
public class BrokerAccessTokenProviderTest {

    @Rule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @BeforeClass
    public static void setupClass() {
        TestingTools.initHadoop();
    }

    @Before
    public void setup() {
        // Make sure we're logged out before every test
        TestingTools.logout();
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

    @Test
    public void testProviderRefreshWhileNotLoggedIn() {
        try {
            Configuration conf = TestingTools.getBrokerConfig();
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
        TestingTools.startServer(new FakeServer(), grpcCleanup);
        AccessToken token;
        Configuration conf = TestingTools.getBrokerConfig();
        TestingTools.login(ALICE);
        token = refresh(conf);
        assertEquals("FakeAccessToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=" + MOCK_BUCKET, token.getToken());
    }

}
