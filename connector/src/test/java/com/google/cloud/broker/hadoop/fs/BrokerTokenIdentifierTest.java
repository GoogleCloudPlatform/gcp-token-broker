package com.google.cloud.broker.hadoop.fs;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.google.cloud.broker.hadoop.fs.TestingTools.*;
import static com.google.cloud.broker.hadoop.fs.SpnegoUtilsTest.TGT_ERROR;
import com.google.cloud.broker.protobuf.GetSessionTokenRequest;
import com.google.cloud.broker.protobuf.GetSessionTokenResponse;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*", "javax.crypto.*", "javax.net.ssl.*", "javax.security.*", "org.ietf.jgss.*"})
@PrepareForTest({GrpcUtils.class})  // Classes to be mocked
public class BrokerTokenIdentifierTest {

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

    public String getSessionToken(Configuration conf) {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        Text service = new Text(MOCK_BUCKET);
        Text owner = new Text(ALICE);
        Text renewer = new Text(YARN);
        binding.bindToFileSystem(new GoogleHadoopFileSystem(), service);
        BrokerTokenIdentifier identifier = new BrokerTokenIdentifier(conf, owner, renewer, null, service);
        return identifier.getSessionToken();
    }

    private static class FakeServer extends TestingTools.FakeBrokerImpl {
        @Override
        public void getSessionToken(GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
            try {
                String authenticatedUser = authenticateUser();
                GetSessionTokenResponse response = GetSessionTokenResponse.newBuilder()
                    .setSessionToken("FakeSessionToken/AuthenticatedUser=" + authenticatedUser + ";Owner=" + request.getOwner() + ";Target=" + request.getTarget())
                    .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
            catch (StatusRuntimeException e) {throw new RuntimeException(e);}
        }
    }

    @Test
    public void testGetSessionTokenProviderRefreshWhileNotLoggedIn() throws Exception {
        try {
            Configuration conf = TestingTools.getBrokerConfig();
            getSessionToken(conf);
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
    public void testGetSessionToken() {
        TestingTools.startServer(new FakeServer(), grpcCleanup);
        Configuration conf = TestingTools.getBrokerConfig();
        TestingTools.login(ALICE);
        String token = getSessionToken(conf);
        assertEquals("FakeSessionToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=" + MOCK_BUCKET, token);
    }
}
