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

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.*;

import com.google.cloud.broker.database.backends.DummyDatabaseBackend;
import com.google.cloud.broker.utils.EnvUtils;
import com.google.cloud.broker.utils.TimeUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.testing.GrpcCleanupRule;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.protobuf.*;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*"})
@PrepareForTest({EnvUtils.class, TimeUtils.class})  // Classes to be mocked
public class BrokerServerTest {

    private static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String MOCK_BUCKET = "gs://example";
    private static final Long SESSION_RENEW_PERIOD = 80000000L;
    private static final Long SESSION_MAXIMUM_LIFETIME = 80000000L;

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @BeforeClass
    public static void setupClass() {
        HashMap<String, String> env = new HashMap(System.getenv());
        env.put("APP_SETTINGS_CLASS", "com.google.cloud.broker.settings.BrokerSettings");
        env.put("APP_SETTING_DATABASE_BACKEND", "com.google.cloud.broker.database.backends.DummyDatabaseBackend");
        env.put("APP_SETTING_REMOTE_CACHE", "com.google.cloud.broker.caching.remote.DummyCache");
        env.put("APP_SETTING_ENCRYPTION_BACKEND", "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
        env.put("APP_SETTING_AUTHENTICATION_BACKEND", "com.google.cloud.broker.authentication.backends.MockAuthenticator");
        env.put("APP_SETTING_PROXY_USER_WHITELIST", "hive@FOO.BAR");
        env.put("APP_SETTING_SESSION_RENEW_PERIOD", SESSION_RENEW_PERIOD.toString());
        env.put("APP_SESSION_MAXIMUM_LIFETIME", SESSION_MAXIMUM_LIFETIME.toString());
        mockStatic(EnvUtils.class);
        when(EnvUtils.getenv()).thenReturn(env);
    }

    @After
    public void teardown() {
        // Clear the database
        ConcurrentMap<String, Object> map = DummyDatabaseBackend.getMap();
        map.clear();
    }

    private BrokerGrpc.BrokerBlockingStub getStub() {
        String serverName = InProcessServerBuilder.generateName();

        try {
            grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(BrokerServer.getServiceDefinition()).build().start());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BrokerGrpc.BrokerBlockingStub stub = BrokerGrpc.newBlockingStub(
            grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        return stub;
    }

    public BrokerGrpc.BrokerBlockingStub SPNEGOify(BrokerGrpc.BrokerBlockingStub stub, String principal) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "Negotiate " + principal);
        stub = MetadataUtils.attachHeaders(stub, metadata);
        return stub;
    }

    @Test
    public void testGetSessionToken() {
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "alice@EXAMPLE.COM");

        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Send the GetSessionToken request
        GetSessionTokenResponse response = stub.getSessionToken(GetSessionTokenRequest.newBuilder()
            .setOwner("alice@EXAMPLE.COM")
            .setRenewer("yarn@FOO.BAR")
            .setScope(SCOPE)
            .setTarget(MOCK_BUCKET)
            .build());

        // Check that the session was created
        Session session = SessionTokenUtils.getSessionFromRawToken(response.getSessionToken());
        assertEquals("alice@EXAMPLE.COM", session.getValue("owner"));
        assertEquals("yarn@FOO.BAR", session.getValue("renewer"));
        assertEquals(SCOPE, session.getValue("scope"));
        assertEquals(MOCK_BUCKET, session.getValue("target"));
        assertEquals(now, session.getValue("creation_time"));
        assertEquals(now + SESSION_RENEW_PERIOD, session.getValue("expires_at"));
    }

    @Test
    public void testCancelSessionToken() {
        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", "alice@EXAMPLE.COM");
        values.put("renewer", "yarn@FOO.BAR");
        Session session = new Session(values);
        Model.save(session);

        // Send the Cancel request
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "yarn@FOO.BAR");
        stub.cancelSessionToken(CancelSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session was deleted
        try {
            Model.get(Session.class, (String) session.getValue("id"));
            fail("DatabaseObjectNotFound not thrown");
        }
        catch (DatabaseObjectNotFound e) {}
    }

    @Test
    public void testCancelSessionToken_WrongRenewer() {
        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", "alice@EXAMPLE.COM");
        values.put("renewer", "yarn@FOO.BAR");
        Session session = new Session(values);
        Model.save(session);

        // Send the Cancel request
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "baz@FOO.BAR");
        try {
            stub.cancelSessionToken(CancelSessionTokenRequest.newBuilder()
                .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Unauthorized renewer: baz@FOO.BAR", e.getStatus().getDescription());
        }

        // Check that the session still exists
        Model.get(Session.class, (String) session.getValue("id"));
    }

    @Test
    public void testRenewSessionToken() {
        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", "alice@EXAMPLE.COM");
        values.put("renewer", "yarn@FOO.BAR");
        values.put("scope", SCOPE);
        values.put("target", MOCK_BUCKET);
        Session session = new Session(values);
        Model.save(session);

        // Change the system time again to simulate elapsing time
        Long newNow = now + 5000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

        // Send the Renew request
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "yarn@FOO.BAR");
        stub.renewSessionToken(RenewSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session's lifetime has been extended
        session = (Session) Model.get(Session.class, (String) session.getValue("id"));
        assertEquals( newNow + SESSION_RENEW_PERIOD, session.getValue("expires_at"));
    }

    @Test
    public void testRenewSessionToken_MaxLifeTime() {
        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", "alice@EXAMPLE.COM");
        values.put("renewer", "yarn@FOO.BAR");
        values.put("scope", SCOPE);
        values.put("target", MOCK_BUCKET);
        Session session = new Session(values);
        Model.save(session);

        // Change the system time again to simulate elapsing time near the maximum lifetime
        Long newNow = now + SESSION_MAXIMUM_LIFETIME - 5000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

        // Send the Renew request
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "yarn@FOO.BAR");
        stub.renewSessionToken(RenewSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session's lifetime has been extended up to the maximum lifetime
        session = (Session) Model.get(Session.class, (String) session.getValue("id"));
        assertEquals( newNow + SESSION_MAXIMUM_LIFETIME, session.getValue("expires_at"));
    }

    @Test
    public void testRenewSessionToken_WrongRenewer() {
        // Create a session in the database
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("owner", "alice@EXAMPLE.COM");
        values.put("renewer", "yarn@FOO.BAR");
        Session session = new Session(values);
        Model.save(session);

        // Send the Renew request
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = SPNEGOify(stub, "baz@FOO.BAR");
        try {
            stub.renewSessionToken(RenewSessionTokenRequest.newBuilder()
                .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Unauthorized renewer: baz@FOO.BAR", e.getStatus().getDescription());
        }

        // Check that the session still exists
        Model.get(Session.class, (String) session.getValue("id"));
    }


    // TODO: "UNAUTHENTICATED: Session token is invalid or has expired"

//    @Test
//    public void testGetAccessToken() {
//        BrokerGrpc.BrokerBlockingStub stub = getStub();
//        stub = SPNEGOify(stub, "alice@EXAMPLE.COM");
//        stub.getAccessToken(GetAccessTokenRequest.newBuilder()
//            .setOwner("alice@EXAMPLE.COM")
//            .setScope(SCOPE)
//            .setTarget(MOCK_BUCKET)
//            .build());
//    }
}