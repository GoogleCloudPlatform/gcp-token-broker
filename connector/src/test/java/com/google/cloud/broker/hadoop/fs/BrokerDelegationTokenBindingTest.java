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

import org.apache.hadoop.conf.Configuration;
import org.ietf.jgss.GSSException;
import com.sun.security.auth.UserPrincipal;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.grpc.inprocess.InProcessServerBuilder;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.cloud.broker.protobuf.BrokerGrpc;
import com.google.cloud.broker.protobuf.GetAccessTokenRequest;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*", "javax.crypto.*", "javax.net.ssl.*", "javax.security.*", "org.ietf.jgss.*"})
@PrepareForTest({SpnegoUtils.class, GrpcUtils.class})  // Classes to be mocked
public class BrokerDelegationTokenBindingTest {

    private static final String KERBEROS_ERROR = "No valid credentials provided (Mechanism level: No valid credentials provided (Mechanism level: Failed to find any Kerberos tgt))";

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    /**
     * ServerInterceptor that asserts that the provided authorization header (i.e. that contains a Spnego or session token) is correct.
     */
    public class AuthorizationHeaderServerInterceptor implements ServerInterceptor {

        private final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

        private String expectedHeader;

        public AuthorizationHeaderServerInterceptor(String expectedHeader) {
            this.expectedHeader = expectedHeader;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
            String authorizationHeader = metadata.get(AUTHORIZATION_METADATA_KEY);
            assertEquals(expectedHeader, authorizationHeader);
            return serverCallHandler.startCall(serverCall, metadata);
        }
    }

    public void refresh(Configuration conf) throws IOException {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        BrokerAccessTokenProvider provider = (BrokerAccessTokenProvider) binding.deployUnbonded();
        provider.setConf(conf);
        provider.refresh();
    }

    @Test
    public void testDeployUnbonded() {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        try {
            BrokerAccessTokenProvider provider = (BrokerAccessTokenProvider) binding.deployUnbonded();
            // TODO...
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void testCreateTokenIdentifier() {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        BrokerTokenIdentifier identifier = (BrokerTokenIdentifier) binding.createEmptyIdentifier();
        // TODO...
    }

    @Test
    public void testCreateEmptyIdentifier() {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        try {
            BrokerTokenIdentifier identifier = (BrokerTokenIdentifier) binding.createTokenIdentifier();
            // TODO...
        } catch (IOException e) {
            fail();
        }
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
                "User is not logged-in with Kerberos or cannot authenticate with the broker. Kerberos error message: " + KERBEROS_ERROR,
                e.getMessage()
            );
        }
    }

    @Test
    public void testGatewayNotLoggedIn() {
        Configuration conf = new Configuration();
        Subject subject = getSubject("alice@EXAMPLE.COM");
        Subject.doAs(subject, (PrivilegedAction<Void>) () -> {
            try {
                new BrokerGateway(conf);
                fail();
            } catch (GSSException e) {
                assertEquals(KERBEROS_ERROR, e.getMessage());
            }
            return null;
        });
    }

    private Subject getSubject(String username) {
        Subject subject = new Subject();
        UserPrincipal principal = new UserPrincipal(username);
        subject.getPrincipals().add(principal);
        return subject;
    }

    private String startServer(String expectedHeader) {
        String serverName = InProcessServerBuilder.generateName();
        try {
            grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(ServerInterceptors.intercept(new BrokerGrpc.BrokerImplBase() {}, new AuthorizationHeaderServerInterceptor(expectedHeader)))
                .build().start());
            return serverName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGatewayLoggedIn() {
        // Start server
        byte[] fakeSpnegoToken = "abcd".getBytes();
        String expectedHeader = "Negotiate " + Base64.getEncoder().encodeToString(fakeSpnegoToken);
        String serverName = startServer(expectedHeader);

        // Mock the SPNEGO method
        mockStatic(SpnegoUtils.class);
        try {
            when(SpnegoUtils.newSPNEGOToken("broker-test-service", "broker-test-host", "EXAMPLE.COM"))
                .thenReturn(fakeSpnegoToken);
        } catch (GSSException e) {fail();}

        // Mock the GRPC methods
        mockStatic(GrpcUtils.class);
        ManagedChannel inProcessChannel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
        when(GrpcUtils.newManagedChannel("broker-test-host", 9999, false, ""))
            .thenReturn(inProcessChannel);
        BrokerGrpc.BrokerBlockingStub stub = BrokerGrpc.newBlockingStub(ClientInterceptors.intercept(inProcessChannel));
        when(GrpcUtils.newStub(inProcessChannel))
            .thenReturn(stub);

        // Set configuration
        Configuration conf = new Configuration();
        conf.set("gcp.token.broker.servicename", "broker-test-service");
        conf.set("gcp.token.broker.uri.hostname", "broker-test-host");
        conf.set("gcp.token.broker.uri.port", "9999");
        conf.set("gcp.token.broker.tls.enabled", "false");

        // Instantiate gateway
        Subject subject = getSubject("alice@EXAMPLE.COM");
        BrokerGateway gateway = Subject.doAs(subject, (PrivilegedAction<BrokerGateway>) () -> {
            try {
                return new BrokerGateway(conf);
            } catch (GSSException e) {
                throw new RuntimeException();
            }
        });

        try {
            gateway.getStub().getAccessToken(GetAccessTokenRequest.getDefaultInstance());
            fail();
        } catch (StatusRuntimeException e) {
            // Expected since the server endpoint isn't implemented.
        }

    }


    // TODO:
    // - Configuration properties: gcp.token.broker.uri.port, gcp.token.broker.realm, etc.
    // - Client sends proper request parameters
    // - assertEquals("broker-test-host:9999", gateway.getManagedChannel().authority());
    // - BrokerSession header

}