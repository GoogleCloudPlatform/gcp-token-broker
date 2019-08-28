package com.google.cloud.broker.hadoop.fs;

import javax.security.auth.Subject;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Base64;

import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;

import com.google.cloud.broker.protobuf.BrokerGrpc;
import org.apache.hadoop.security.KerberosAuthException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authentication.util.KerberosName;


public class TestingTools {

    public static final String REALM = "EXAMPLE.COM";
    public static final String BROKER_HOST = "testhost";
    public static final String BROKER_NAME = "broker";
    public static final String MOCK_BUCKET = "gs://example";
    public static final String ALICE = "alice@EXAMPLE.COM";
    public static final String YARN = "yarn/testhost@FOOR.COM";


    public static void startServer(FakeBrokerImpl fakeServer, GrpcCleanupRule grpcCleanup) {
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


    public static void login(String user) {
        String username = user.split("@")[0];
        try {
            UserGroupInformation.loginUserFromKeytab(user, "/etc/security/keytabs/users/" + username + ".keytab");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void logout() {
        try {
            UserGroupInformation ugi = UserGroupInformation.getLoginUser();
            ugi.logoutUserFromKeytab();
        }
        catch (KerberosAuthException e) {
            // No user was in fact logged in. Ignore the error.
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Configuration getBrokerConfig() {
        Configuration conf = new Configuration();
        conf.set("gcp.token.broker.uri.hostname", BROKER_HOST);
        conf.set("gcp.token.broker.uri.port", "1234");
        conf.set("gcp.token.broker.servicename", BROKER_NAME);
        conf.set("gcp.token.broker.realm", REALM);
        conf.set("gcp.token.broker.tls.enabled", "false");
        return conf;
    }

    public static void initHadoop() {
        Configuration conf = new Configuration();
        conf.set("hadoop.security.authentication", "kerberos");
        UserGroupInformation.setConfiguration(conf);
        KerberosName.setRules("DEFAULT");
    }

    public static class AuthorizationHeaderServerInterceptor implements ServerInterceptor {

        public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        public static final Context.Key<String> AUTHORIZATION_CONTEXT_KEY = Context.key("AuthorizationHeader");

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
            String authorizationHeader = metadata.get(AUTHORIZATION_METADATA_KEY);
            Context ctx = Context.current().withValue(AUTHORIZATION_CONTEXT_KEY, authorizationHeader);
            return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
        }
    }


    static class FakeBrokerImpl extends BrokerGrpc.BrokerImplBase {

        protected String authenticateUser () {
            String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();
            String spnegoToken = authorizationHeader.split("\\s")[1];

            // Let the broker decrypt the token and verify the user's identity
            Subject broker = SpnegoUtilsTest.login("broker");
            return Subject.doAs(broker, (PrivilegedAction<String>) () ->
                SpnegoUtilsTest.decryptToken(Base64.getDecoder().decode(spnegoToken.getBytes())
            ));
        }

    }

}
