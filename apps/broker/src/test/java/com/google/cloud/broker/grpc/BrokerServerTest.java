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

package com.google.cloud.broker.grpc;

import static org.junit.Assert.*;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.grpc.testing.GrpcCleanupRule;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.google.cloud.broker.protobuf.BrokerGrpc;
import com.google.cloud.broker.protobuf.GetSessionTokenResponse;
import com.google.cloud.broker.protobuf.GetSessionTokenRequest;


public class BrokerServerTest {

    private String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
    private String MOCK_BUCKET = "gs://example";

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void before() {
        environmentVariables.set("APP_SETTINGS_CLASS", "com.google.cloud.broker.settings.BrokerSettings");
        environmentVariables.set("APP_SETTING_DATABASE_BACKEND", "com.google.cloud.broker.database.backends.JDBCBackend");
        environmentVariables.set("APP_SETTING_DATABASE_JDBC_URL", "jdbc:sqlite::memory:");
        environmentVariables.set("APP_SETTING_ENCRYPTION_BACKEND", "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
        environmentVariables.set("APP_SETTING_AUTHENTICATION_BACKEND", "com.google.cloud.broker.authentication.backends.MockAuthenticator");
        environmentVariables.set("APP_SETTING_PROXY_USER_WHITELIST", "hive@FOO.BAR");

        // Initialize the database
        AbstractDatabaseBackend backend = AbstractDatabaseBackend.getInstance();
        backend.initializeDatabase();
    }


    private BrokerGrpc.BrokerBlockingStub getStub() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
            .forName(serverName).directExecutor().addService(BrokerServer.getServiceDefinition()).build().start());

        BrokerGrpc.BrokerBlockingStub stub = BrokerGrpc.newBlockingStub(
            grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        return stub;
    }

    @Test
    public void testGetSessionToken() throws Exception {
        BrokerGrpc.BrokerBlockingStub stub = getStub();

        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "alice@EXAMPLE.COM");
        stub = MetadataUtils.attachHeaders(stub, metadata);

        GetSessionTokenResponse response = stub.getSessionToken(GetSessionTokenRequest.newBuilder()
            .setOwner("alice@EXAMPLE.COM")
            .setRenewer("yarn@FOO.BAR")
            .setScope(SCOPE)
            .setTarget(MOCK_BUCKET)
            .build());

        Session session = SessionTokenUtils.getSessionFromRawToken(response.getSessionToken());
        assertEquals(session.getValue("owner"), "alice@EXAMPLE.COM");
        assertEquals(session.getValue("renewer"), "yarn@FOO.BAR");
        assertEquals(session.getValue("scope"), SCOPE);
        assertEquals(session.getValue("target"), MOCK_BUCKET);
    }

}