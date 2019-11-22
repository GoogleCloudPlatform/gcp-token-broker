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

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authentication.util.KerberosName;
import org.ietf.jgss.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;

import com.google.cloud.broker.testing.FakeKDC;
import com.google.cloud.broker.protobuf.BrokerGrpc;


class TestingTools {

    static final String REALM = "EXAMPLE.COM";
    static final String BROKER_HOST = "testhost";
    static final String BROKER_NAME = "broker";
    static final String MOCK_BUCKET = "gs://example";
    static final String BROKER = BROKER_NAME + "/" + BROKER_HOST + "@" + REALM;
    static final String ALICE = "alice@" + REALM;
    static final String YARN = "yarn/testhost@FOO.BAR";


    /**
     * Starts a live instance of a mock implementation of the broker server.
     */
    static void startServer(FakeBrokerImpl fakeServer, GrpcCleanupRule grpcCleanup) {
        String serverName = InProcessServerBuilder.generateName();
        try {
            grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(ServerInterceptors.intercept(fakeServer, new AuthorizationHeaderServerInterceptor()))
                .build().start());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
        BrokerGrpc.BrokerBlockingStub stub = BrokerGrpc.newBlockingStub(channel);

        mockStatic(GrpcUtils.class);
        when(GrpcUtils.newManagedChannel(BROKER_HOST, 1234, false, "")).thenReturn(channel);
        when(GrpcUtils.newStub(channel)).thenReturn(stub);
    }

    static String decryptToken(byte[] token) {
        try {
            GSSManager manager = GSSManager.getInstance();
            Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
            GSSCredential serverCreds = manager.createCredential(null,
                GSSCredential.DEFAULT_LIFETIME, spnegoOid, GSSCredential.ACCEPT_ONLY);
            GSSContext context = manager.createContext(serverCreds);
            context.acceptSecContext(token, 0, token.length);
            return context.getSrcName().toString();
        } catch (GSSException e) {
            throw new RuntimeException(e);
        }
    }

    static Configuration getBrokerConfig() {
        Configuration conf = new Configuration();
        conf.set("gcp.token.broker.uri.hostname", BROKER_HOST);
        conf.set("gcp.token.broker.uri.port", "1234");
        conf.set("gcp.token.broker.servicename", BROKER_NAME);
        conf.set("gcp.token.broker.realm", REALM);
        conf.set("gcp.token.broker.tls.enabled", "false");
        return conf;
    }

    static void initHadoop() {
        Configuration conf = new Configuration();
        conf.set("hadoop.security.authentication", "kerberos");
        UserGroupInformation.setConfiguration(conf);
        KerberosName.setRules("DEFAULT");
    }

    public static class AuthorizationHeaderServerInterceptor implements ServerInterceptor {

        static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        static final Context.Key<String> AUTHORIZATION_CONTEXT_KEY = Context.key("AuthorizationHeader");

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
            String authorizationHeader = metadata.get(AUTHORIZATION_METADATA_KEY);
            Context ctx = Context.current().withValue(AUTHORIZATION_CONTEXT_KEY, authorizationHeader);
            return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
        }
    }


    /**
     * Test implementation of the broker server, including SPNEGO authentication for incoming requests.
     */
    static class FakeBrokerImpl extends BrokerGrpc.BrokerImplBase {
        FakeKDC fakeKDC;

        FakeBrokerImpl(FakeKDC fakeKDC) {
            this.fakeKDC = fakeKDC;
        }

        String authenticateUser() {
            String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();
            String spnegoToken = authorizationHeader.split("\\s")[1];

            // Let the broker decrypt the token and verify the user's identity
            Subject broker = fakeKDC.login(BROKER);
            return Subject.doAs(broker, (PrivilegedAction<String>) () ->
                decryptToken(Base64.getDecoder().decode(spnegoToken.getBytes())
            ));
        }

    }

}