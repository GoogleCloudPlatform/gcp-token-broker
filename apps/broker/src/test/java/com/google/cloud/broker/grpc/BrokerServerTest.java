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
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
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

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.backends.DummyDatabaseBackend;
import com.google.cloud.broker.utils.EnvUtils;
import com.google.cloud.broker.utils.TimeUtils;
import com.google.cloud.broker.sessions.Session;
import com.google.cloud.broker.sessions.SessionTokenUtils;
import static com.google.cloud.broker.protobuf.BrokerGrpc.BrokerBlockingStub;
import com.google.cloud.broker.protobuf.*;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*"})
@PrepareForTest({EnvUtils.class, TimeUtils.class})  // Classes to be mocked
public class BrokerServerTest {

    // TODO: Still needs tests:
    //  - "UNAUTHENTICATED: Session token is invalid or has expired"
    //  - Proxy users
    //  - Not whitelisted scopes

    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final String GCS = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String MOCK_BUCKET = "gs://example";
    private static final Long SESSION_RENEW_PERIOD = 80000000L;
    private static final Long SESSION_MAXIMUM_LIFETIME = 80000000L;

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @BeforeClass
    public static void setupClass() {
        AppSettings.reset();
        HashMap<String, String> env = new HashMap(System.getenv());
        env.put("APP_SETTING_PROVIDER", "com.google.cloud.broker.accesstokens.providers.MockProvider");
        env.put("APP_SETTING_DATABASE_BACKEND", "com.google.cloud.broker.database.backends.DummyDatabaseBackend");
        env.put("APP_SETTING_REMOTE_CACHE", "com.google.cloud.broker.caching.remote.DummyCache");
        env.put("APP_SETTING_ENCRYPTION_BACKEND", "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
        env.put("APP_SETTING_AUTHENTICATION_BACKEND", "com.google.cloud.broker.authentication.backends.MockAuthenticator");
        env.put("APP_SETTING_SCOPE_WHITELIST", "https://www.googleapis.com/auth/devstorage.read_write,https://www.googleapis.com/auth/bigquery");
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

    private BrokerBlockingStub getStub() {
        String serverName = InProcessServerBuilder.generateName();

        try {
            grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(BrokerServer.getServiceDefinition()).build().start());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return BrokerGrpc.newBlockingStub(
            grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
    }

    public BrokerBlockingStub addSPNEGOTokenToMetadata(BrokerBlockingStub stub, String principal) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "Negotiate " + principal);
        stub = MetadataUtils.attachHeaders(stub, metadata);
        return stub;
    }

    public BrokerBlockingStub addSessionTokenToMetadata(BrokerBlockingStub stub, Session session) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "BrokerSession " + SessionTokenUtils.marshallSessionToken(session));
        stub = MetadataUtils.attachHeaders(stub, metadata);
        return stub;
    }

    private Session createSession() {
        // Create a session in the database
        Session session = new Session(null, ALICE, "yarn@FOO.BAR", MOCK_BUCKET, GCS, null, null, null);
        AbstractDatabaseBackend.getInstance().save(session);
        return session;
    }

    @Test
    public void testGetSessionToken() {
        BrokerGrpc.BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, ALICE);

        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Send the GetSessionToken request
        GetSessionTokenResponse response = stub.getSessionToken(GetSessionTokenRequest.newBuilder()
            .setOwner(ALICE)
            .setRenewer("yarn@FOO.BAR")
            .setScope(GCS)
            .setTarget(MOCK_BUCKET)
            .build());

        // Check that the session was created
        Session session = SessionTokenUtils.getSessionFromRawToken(response.getSessionToken());
        assertEquals(ALICE, session.getOwner());
        assertEquals("yarn@FOO.BAR", session.getRenewer());
        assertEquals(GCS, session.getScope());
        assertEquals(MOCK_BUCKET, session.getTarget());
        assertEquals(now, session.getCreationTime());
        assertEquals(now + SESSION_RENEW_PERIOD, session.getExpiresAt().longValue());
    }

    @Test
    public void testCancelSessionToken() {
        // Create a session in the database
        Session session = createSession();

        // Send the Cancel request
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, "yarn@FOO.BAR");
        stub.cancelSessionToken(CancelSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session was deleted
        try {
            AbstractDatabaseBackend.getInstance().get(Session.class, session.getId());
            fail("DatabaseObjectNotFound not thrown");
        }
        catch (DatabaseObjectNotFound e) {}
    }

    @Test
    public void testCancelSessionToken_WrongRenewer() {
        // Create a session in the database
        Session session = createSession();

        // Send the Cancel request
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, "baz@FOO.BAR");
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
        AbstractDatabaseBackend.getInstance().get(Session.class, session.getId());
    }

    @Test
    public void testRenewSessionToken() {
        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Create a session in the database
        Session session = createSession();

        // Change the system time again to simulate elapsing time
        Long newNow = now + 5000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

        // Send the Renew request
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, "yarn@FOO.BAR");
        stub.renewSessionToken(RenewSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session's lifetime has been extended
        session = (Session) AbstractDatabaseBackend.getInstance().get(Session.class, session.getId());
        assertEquals( newNow + SESSION_RENEW_PERIOD, session.getExpiresAt().longValue());
    }

    @Test
    public void testRenewSessionToken_MaxLifeTime() {
        // Mock the system time
        mockStatic(TimeUtils.class);
        Long now = 1000000000000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(now);

        // Create a session in the database
        Session session = createSession();

        // Change the system time again to simulate elapsing time near the maximum lifetime
        Long newNow = now + SESSION_MAXIMUM_LIFETIME - 5000L;
        PowerMockito.when(TimeUtils.currentTimeMillis()).thenReturn(newNow);

        // Send the Renew request
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, "yarn@FOO.BAR");
        stub.renewSessionToken(RenewSessionTokenRequest.newBuilder()
            .setSessionToken(SessionTokenUtils.marshallSessionToken(session))
            .build());

        // Check that the session's lifetime has been extended up to the maximum lifetime
        session = (Session) AbstractDatabaseBackend.getInstance().get(Session.class, (String) session.getId());
        assertEquals( newNow + SESSION_MAXIMUM_LIFETIME, session.getExpiresAt().longValue());
    }

    @Test
    public void testRenewSessionToken_WrongRenewer() {
        // Create a session in the database
        Session session = createSession();

        // Send the Renew request
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, "baz@FOO.BAR");
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
        AbstractDatabaseBackend.getInstance().get(Session.class, session.getId());
    }

    @Test
    public void testGetAccessToken_DirectAuth() {
        BrokerBlockingStub stub = getStub();
        stub = addSPNEGOTokenToMetadata(stub, ALICE);
        GetAccessTokenResponse response = stub.getAccessToken(GetAccessTokenRequest.newBuilder()
            .setOwner(ALICE)
            .setScope(GCS)
            .setTarget(MOCK_BUCKET)
            .build());
        assertEquals("FakeAccessToken/Owner=" + ALICE.toLowerCase() + ";Scope=" + GCS, response.getAccessToken());
        assertEquals(999999999L, response.getExpiresAt());
    }


    @Test
    public void testGetAccessToken_DelegatedAuth_Success() {
        // Create a session in the database
        Session session = createSession();

        // Add the session token to the request's metadata
        BrokerBlockingStub stub = getStub();
        stub = addSessionTokenToMetadata(stub, session);

        // Send the GetAccessToken request
        GetAccessTokenResponse response = stub.getAccessToken(GetAccessTokenRequest.newBuilder()
            .setOwner(ALICE)
            .setScope(GCS)
            .setTarget(MOCK_BUCKET)
            .build());

        assertEquals("FakeAccessToken/Owner=" + ALICE.toLowerCase() + ";Scope=" + GCS, response.getAccessToken());
        assertEquals(999999999L, response.getExpiresAt());
    }

    @Test
    public void testGetAccessToken_DelegatedAuth_ParameterMisMatch() {
        // Create a session in the database
        Session session = createSession();

        // Add the session token to the request's metadata
        BrokerBlockingStub stub = getStub();
        stub = addSessionTokenToMetadata(stub, session);

        // Send the GetAccessToken request, with wrong owner
        try {
            stub.getAccessToken(GetAccessTokenRequest.newBuilder()
                .setOwner("bob@EXAMPLE.COM")
                .setScope(GCS)
                .setTarget(MOCK_BUCKET)
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Owner mismatch", e.getStatus().getDescription());
        }

        // Send the GetAccessToken request, with wrong owner
        try {
            stub.getAccessToken(GetAccessTokenRequest.newBuilder()
                .setOwner(ALICE)
                .setScope("https://www.googleapis.com/auth/bigquery")
                .setTarget(MOCK_BUCKET)
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Scope mismatch", e.getStatus().getDescription());
        }

        // Send the GetAccessToken request, with wrong owner
        try {
            stub.getAccessToken(GetAccessTokenRequest.newBuilder()
                .setOwner(ALICE)
                .setScope(GCS)
                .setTarget("gs://test")
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Target mismatch", e.getStatus().getDescription());
        }
    }

}