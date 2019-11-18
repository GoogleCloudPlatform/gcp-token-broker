/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.broker.authorization;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import ch.qos.logback.classic.Level;

import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.oauth.GoogleClientSecretsLoader;
import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.settings.AppSettings;

public class Authorizer implements AutoCloseable {
    private Server server;
    private static SoySauce soySauce;

    static {
        SoyFileSet sfs = SoyFileSet.builder()
            .add(Resources.getResource("index.soy"))
            .add(Resources.getResource("success.soy"))
            .build();
        soySauce = sfs.compileTemplates();
    }

    private static final HttpTransport HTTP_TRANSPORT = new ApacheHttpTransport();
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final GoogleClientSecrets CLIENT_SECRETS = GoogleClientSecretsLoader.getSecrets();
    private static final Credential.AccessMethod ACCESS_METHOD = BearerToken.queryParameterAccessMethod();
    private static final String CODE_PARAM = "code";
    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String AUTH_SERVER_URL = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Set<String> SCOPES = ImmutableSet.of(
        "https://www.googleapis.com/auth/devstorage.read_write",
        "email",
        "profile");
    private static final GoogleAuthorizationCodeFlow FLOW = new GoogleAuthorizationCodeFlow
        .Builder(HTTP_TRANSPORT,
        JSON_FACTORY,
        CLIENT_SECRETS,
        SCOPES)
        .setAuthorizationServerEncodedUrl(AUTH_SERVER_URL)
        .setTokenServerUrl(new GenericUrl(TOKEN_URL))
        .setMethod(ACCESS_METHOD)
        .setAccessType("offline").setApprovalPrompt("force") // "offline" and "force" are both required to get a refresh token
        .build();
    private static final String GOOGLE_LOGIN_URI = "/google/login";
    private static final String GOOGLE_OAUTH2_CALLBACK_URI = "/google/oauth2callback";
    private static final String host = AppSettings.getProperty("AUTHORIZER_HOST", "0.0.0.0");
    private static final int port = Integer.parseInt(AppSettings.getProperty("AUTHORIZER_PORT", "8080"));
    private static final boolean enableSpnego = Boolean.parseBoolean(AppSettings.getProperty("AUTHORIZER_ENABLE_SPNEGO", "false"));
    @VisibleForTesting
    AuthorizerServlet servlet;


    public static void main(String[] args) throws Exception {
        Authorizer authorizer = new Authorizer();
        authorizer.start();
        authorizer.join();
    }

    private static void setLoggingLevel() {
        Level level = Level.toLevel(AppSettings.getProperty("LOGGING_LEVEL", "INFO"));
        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("org.eclipse.jetty");
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger;
        logbackLogger.setLevel(level);
    }

    public Authorizer() throws LoginException {
        // Initialize the logging level
        setLoggingLevel();

        int opts = ServletContextHandler.GZIP | ServletContextHandler.SECURITY;
        ServletContextHandler ctx = new ServletContextHandler(opts);
        ctx.setContextPath("/");
        if (enableSpnego) {
            // Require authentication
            Constraint constraint = new Constraint();
            constraint.setAuthenticate(true);
            constraint.setName("authn");
            constraint.setRoles(new String[]{"user"});

            // Require authentication on all paths
            ConstraintMapping cMap = new ConstraintMapping();
            cMap.setPathSpec("/*");
            cMap.setConstraint(constraint);

            // Require authentication on all paths using SPNEGO
            ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
            csh.addConstraintMapping(cMap);

            String principal = AppSettings.requireProperty("AUTHORIZER_PRINCIPAL");
            String keytabPath = AppSettings.requireProperty("AUTHORIZER_KEYTAB");
            LoginService loginService = new Spnego.SpnegoLoginService(principal, keytabPath, "user");
            Spnego.SpnegoLoginAuthenticator authenticator = new Spnego.SpnegoLoginAuthenticator();

            csh.setLoginService(loginService);
            csh.setAuthenticator(authenticator);

            csh.setIdentityService(new DefaultIdentityService());

            // Attach to ServletContextHandler
            ctx.setSecurityHandler(csh);
        }

        // Instantiate the server
        server = new Server(new InetSocketAddress(host, port));
        servlet = new AuthorizerServlet();
        ctx.addServlet(new ServletHolder(servlet), "/");
        server.setHandler(ctx);
        server.setStopAtShutdown(true);

        // Force the server to respect X-Forwarded-* headers for when requests are
        // forwarded by a proxy. This makes sure, for example, that the "https" scheme
        // is preserved when TLS is terminated by a load balancer.
        for (Connector connector : server.getConnectors()) {
            ConnectionFactory connectionFactory = connector.getDefaultConnectionFactory();
            if(connectionFactory instanceof HttpConnectionFactory) {
                HttpConnectionFactory defaultConnectionFactory = (HttpConnectionFactory) connectionFactory;
                HttpConfiguration httpConfiguration = defaultConnectionFactory.getHttpConfiguration();
                httpConfiguration.addCustomizer(new ForwardedRequestCustomizer());
            }
        }
    }

    public static class UserInfo extends GenericJson {
        @Key
        private String email;
        String getEmail() {
            return email;
        }
    }

    private static UserInfo getUserInfo(Credential credential) throws IOException {
        HttpRequest request = HTTP_TRANSPORT
            .createRequestFactory(credential)
            .buildGetRequest(new GenericUrl(USER_INFO_URI));
        request.getHeaders().setContentType("application/json");
        request.setParser(new JsonObjectParser(JSON_FACTORY));
        HttpResponse response = request.execute();
        return response.parseAs(UserInfo.class);
    }

    void start() throws Exception {
        if (server != null){
            server.start();
        }
    }

    private void join() throws Exception {
        if (server != null){
            server.join();
        }
    }

    @Override
    public void close() throws Exception {
        if (server != null && !server.isStopped()) {
            server.stop();
        }
    }

    public static class AuthorizerServlet extends HttpServlet {

        /**
         * Stores the given refresh token in the database.
         */
        @VisibleForTesting
        void saveRefreshToken(String id, String value){
            byte[] encryptedValue = AbstractEncryptionBackend.getInstance().encrypt(value.getBytes());
            RefreshToken refreshToken = new RefreshToken(id, encryptedValue, null);
            AbstractDatabaseBackend.getInstance().save(refreshToken);
        }

        /**
         * Handler for the index page.
         */
        private void handleIndex(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/html");
            Map<String, Object> data = ImmutableMap.<String, Object>builder()
                .put("GOOGLE_LOGIN_URI", GOOGLE_LOGIN_URI)
                .build();
            String content = soySauce
                .renderTemplate("Authorizer.Templates.index")
                .setData(data)
                .renderHtml()
                .get()
                .getContent();
            response.getWriter().write(content);
        }

        /**
         * Handler for login redirect endpoint.
         */
        private void handleLoginRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.sendRedirect(FLOW
                .newAuthorizationUrl()
                .setRedirectUri(((Request) request).getRootURL() + GOOGLE_OAUTH2_CALLBACK_URI)
                .build());
        }

        /**
         * Handler for the OAuth callback endpoint.
         */
        private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
            // Ensure that the authorization code is provided
            String authzCode = request.getParameter(CODE_PARAM);
            if (authzCode == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Confirm the authorization with Google
            final TokenResponse tokenResponse = FLOW
                .newTokenRequest(authzCode)
                .setRedirectUri(((Request) request).getRootURL() + GOOGLE_OAUTH2_CALLBACK_URI)
                .execute();

            // Retrieve some details about the Google identity
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(tokenResponse.getAccessToken());
            UserInfo user = getUserInfo(credential);
            response.setContentType("text/html");

            // Save the refresh token in the database
            saveRefreshToken(user.getEmail(), tokenResponse.getRefreshToken());

            // Generate the HTML response
            String content = soySauce
                .renderTemplate("Authorizer.Templates.success")
                .renderHtml()
                .get()
                .getContent();
            PrintWriter out = response.getWriter();
            out.write(content);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String requestUri = request.getRequestURI();
            // Index page
            switch (requestUri) {
                case "/":
                    handleIndex(request, response);
                    break;
                // Login endpoint: redirect to Google login
                case GOOGLE_LOGIN_URI:
                    handleLoginRedirect(request, response);
                    break;
                // OAuth Callback endpoint: process authorization code returned by Google
                case GOOGLE_OAUTH2_CALLBACK_URI:
                    handleCallback(request, response);
                    break;
                // Return 404 for everything else
                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().println("<h1>Page not found</h1>");
                    break;
            }
        }
    }
}
