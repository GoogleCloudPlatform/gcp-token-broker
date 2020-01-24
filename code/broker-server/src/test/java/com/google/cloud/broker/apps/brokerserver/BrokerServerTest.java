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

package com.google.cloud.broker.apps.brokerserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.google.cloud.broker.apps.brokerserver.protobuf.BrokerGrpc.BrokerBlockingStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import com.google.cloud.broker.apps.brokerserver.protobuf.*;
import com.google.cloud.broker.apps.brokerserver.sessions.Session;
import com.google.cloud.broker.apps.brokerserver.sessions.SessionTokenUtils;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.database.backends.DummyDatabaseBackend;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.TimeUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*"})
@PrepareForTest({TimeUtils.class})  // Classes to be mocked
public class BrokerServerTest {

    // TODO: Still needs tests:
    //  - "UNAUTHENTICATED: Session token is invalid or has expired"
    //  - Proxy users
    //  - Not whitelisted scopes

    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");
    private static final String MOCK_BUCKET = "gs://example";
    private static final Long SESSION_RENEW_PERIOD = 80000000L;
    private static final Long SESSION_MAXIMUM_LIFETIME = 160000000L;

    private static String configFileBackup;

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @BeforeClass
    public static void setupClass() throws IOException {
        // Note: Here we're changing the environment variables instead of using the SettingsOverride class,
        // to let the test settings apply both to the main thread and to the thread where the test server
        // is running.
        Map<String, String> map = Maps.newHashMap();
        map.put(AppSettings.PROVIDER_BACKEND, "com.google.cloud.broker.apps.brokerserver.accesstokens.providers.MockProvider");
        map.put(AppSettings.DATABASE_BACKEND, "com.google.cloud.broker.database.backends.DummyDatabaseBackend");
        map.put(AppSettings.REMOTE_CACHE, "com.google.cloud.broker.caching.remote.DummyCache");
        map.put(AppSettings.ENCRYPTION_BACKEND, "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend");
        map.put(AppSettings.AUTHENTICATION_BACKEND, "com.google.cloud.broker.authentication.backends.MockAuthenticator");
        map.put(AppSettings.SCOPES_WHITELIST, "[\"https://www.googleapis.com/auth/devstorage.read_write\", \"https://www.googleapis.com/auth/bigquery\"]");
        map.put(AppSettings.SESSION_RENEW_PERIOD, SESSION_RENEW_PERIOD.toString());
        map.put(AppSettings.SESSION_MAXIMUM_LIFETIME, SESSION_MAXIMUM_LIFETIME.toString());
        map.put(AppSettings.USER_MAPPER, "com.google.cloud.broker.usermapping.MockUserMapper");

        // Keep reference to old config file, if any
        configFileBackup = System.getProperty("config.file");

        // Override config file
        Path configFilePath = Files.createTempFile("test", ".conf");
        Files.writeString(configFilePath, Joiner.on("\n").withKeyValueSeparator("=").join(map));
        System.setProperty("config.file", configFilePath.toString());
    }

    @After
    public void tearDown() {
        // Restore old config file, if any
        if (configFileBackup != null) {
            System.setProperty("config.file", configFileBackup);
        }
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
        Session session = new Session(
            null,
            ALICE,
            "yarn@FOO.BAR",
            MOCK_BUCKET,
            String.join(",", SCOPES),
            null,
            null,
            null);
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
            .addAllScopes(SCOPES)
            .setTarget(MOCK_BUCKET)
            .build());

        // Check that the session was created
        Session session = SessionTokenUtils.getSessionFromRawToken(response.getSessionToken());
        assertEquals(ALICE, session.getOwner());
        assertEquals("yarn@FOO.BAR", session.getRenewer());
        assertEquals(SCOPES, Arrays.asList(session.getScopes().split(",")));
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
        session = (Session) AbstractDatabaseBackend.getInstance().get(Session.class, session.getId());
        assertEquals(now + SESSION_MAXIMUM_LIFETIME, session.getExpiresAt().longValue());
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
            .addAllScopes(SCOPES)
            .setTarget(MOCK_BUCKET)
            .build());
        assertEquals(
            "FakeAccessToken/GoogleIdentity=alice@altostrat.com;Scopes=" + String.join(",", SCOPES),
            response.getAccessToken());
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
            .addAllScopes(SCOPES)
            .setTarget(MOCK_BUCKET)
            .build());

        assertEquals(
            "FakeAccessToken/GoogleIdentity=alice@altostrat.com;Scopes=" + String.join(",", SCOPES),
            response.getAccessToken());
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
                .addAllScopes(SCOPES)
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
                .addAllScopes(List.of("https://www.googleapis.com/auth/bigquery"))
                .setTarget(MOCK_BUCKET)
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Scopes mismatch", e.getStatus().getDescription());
        }

        // Send the GetAccessToken request, with wrong owner
        try {
            stub.getAccessToken(GetAccessTokenRequest.newBuilder()
                .setOwner(ALICE)
                .addAllScopes(SCOPES)
                .setTarget("gs://test")
                .build());
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Target mismatch", e.getStatus().getDescription());
        }
    }

}