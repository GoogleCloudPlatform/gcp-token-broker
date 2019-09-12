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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.sun.security.auth.module.Krb5LoginModule;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.SpnegoUserPrincipal;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.security.Constraint;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import sun.security.jgss.GSSUtil;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Spnego {
    private String clientPrincipal;
    private String serverPrincipal;
    private byte[] returnToken;

    public Spnego() {
    }

    public Spnego(String clientPrincipal, String serverPrincipal, byte[] returnToken) {
        this.clientPrincipal = clientPrincipal;
        this.serverPrincipal = serverPrincipal;
        this.returnToken = returnToken;
    }

    public String getServerPrincipal() {
        return serverPrincipal;
    }

    public String getClientPrincipal() {
        return clientPrincipal;
    }

    public byte[] getReturnToken() {
        return returnToken;
    }

    public boolean isAuthenticated() {
        return clientPrincipal != null;
    }

    public static Spnego failure() {
        return new Spnego();
    }

    /**
     * Authenticate an SPNego token as a Subject
     *
     * @param subject  logged-in Subject
     * @param tokenEnc Base64 encoded token
     * @return AuthnResult
     */
    public static Spnego acceptToken(Subject subject, String tokenEnc) {
        if (subject == null) {
            return Spnego.failure();
        }
        return Subject.doAs(subject, new AuthenticateSpnegoToken(tokenEnc));
    }

    /**
     * PrivilegedAction to acceptToken an SPNego token
     */
    private static class AuthenticateSpnegoToken implements PrivilegedAction<Spnego> {
        private byte[] token;

        public AuthenticateSpnegoToken(String tokenEnc) {
            // Base64 NOT Base64Url
            this.token = BaseEncoding.base64().decode(tokenEnc);
        }

        @Override
        public Spnego run() {
            try {
                return acceptToken(token);
            } catch (GSSException e) {
                return Spnego.failure();
            }
        }
    }

    private static Spnego acceptToken(byte[] token) throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSCredential serverCredential = manager.createCredential(null, GSSCredential.DEFAULT_LIFETIME, GSSUtil.GSS_SPNEGO_MECH_OID, GSSCredential.ACCEPT_ONLY);
        GSSContext ctx = manager.createContext(serverCredential);
        Spnego result = acceptToken(ctx, token);
        ctx.dispose();
        return result;
    }

    private static Spnego acceptToken(GSSContext ctx, byte[] token) {
        try {
            byte[] returnToken = ctx.acceptSecContext(token, 0, token.length);
            if (!ctx.isEstablished()) {
                return Spnego.failure();
            }
            String clientPrincipal = ctx.getSrcName().toString();
            String serverPrincipal = ctx.getTargName().toString();
            return new Spnego(clientPrincipal, serverPrincipal, returnToken);
        } catch (GSSException e) {
            return Spnego.failure();
        }
    }


    /**
     * Method to get an SPNego token with a logged-in Subject
     *
     * @param subject         logged-in Subject
     * @param serverPrincipal target server
     * @return byte[] SPNego token
     */
    public static byte[] getToken(Subject subject, String serverPrincipal) {
        return Subject.doAs(subject, new GetSpnegoToken(serverPrincipal));
    }

    /**
     * PrivilegedAction to obtain an SPNego token for a server principal
     */
    public static final class GetSpnegoToken implements PrivilegedAction<byte[]> {
        private String serverPrincipal;

        public GetSpnegoToken(String serverPrincipal) {
            this.serverPrincipal = serverPrincipal;
        }

        @Override
        public byte[] run() {
            try {
                return getToken(serverPrincipal);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * to be used within a privileged action
     */
    public static byte[] getToken(String serverPrincipal) throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSName serverName = manager.createName(serverPrincipal, GSSUtil.NT_GSS_KRB5_PRINCIPAL, GSSUtil.GSS_SPNEGO_MECH_OID);
        GSSContext ctx = manager.createContext(serverName, GSSUtil.GSS_SPNEGO_MECH_OID, null, GSSCredential.DEFAULT_LIFETIME);
        ctx.requestMutualAuth(true);
        ctx.requestCredDeleg(true);
        byte[] token = ctx.initSecContext(new byte[0], 0, 0);
        ctx.dispose();
        return token;
    }

    public static CloseableHttpClient httpClient(String keytabPath, String clientPrincipal, String serverPrincipal) throws LoginException {
        AuthScope any = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM);

        AuthSchemeProvider asp = new SpnegoAuthSchemeProvider(serverPrincipal);
        Credentials creds = new KeytabCredentials(clientPrincipal, keytabPath);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(any, creds);
        Lookup<AuthSchemeProvider> authSchemeRegistry =
            RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.SPNEGO, asp).build();

        return HttpClientBuilder.create()
            .setSSLHostnameVerifier(new NoopHostnameVerifier())
            .setDefaultCredentialsProvider(credentialsProvider)
            .setDefaultAuthSchemeRegistry(authSchemeRegistry)
            .setRedirectStrategy(new NoRedirectStrategy())
            .build();
    }

    public static class NoRedirectStrategy implements RedirectStrategy {

        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
            return false;
        }

        @Override
        public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            throw new NotImplementedException();
        }
    }

    public static class KeytabCredentials implements Credentials {

        private final KerberosPrincipal userPrincipal;
        private final Subject subject;

        public KeytabCredentials(String principalName, String keytab) throws LoginException {
            this.userPrincipal = new KerberosPrincipal(principalName);
            this.subject = login(principalName, keytab, null, true);
        }

        @Override
        public Principal getUserPrincipal() {
            return userPrincipal;
        }

        @Override
        public String getPassword() {
            return null;
        }

        public Subject getSubject() {
            return subject;
        }

    }

    public static class SpnegoAuthSchemeProvider implements AuthSchemeProvider {
        private String serverPrincipal;

        public SpnegoAuthSchemeProvider(String serverPrincipal) {
            this.serverPrincipal = serverPrincipal;
        }

        public AuthScheme create(HttpContext context) {
            return new FixedPrincipalSpnego(serverPrincipal);
        }
    }

    public static class FixedPrincipalSpnego extends SPNegoScheme {
        private String serverPrincipal;

        public FixedPrincipalSpnego(String serverPrincipal) {
            super(true, false);
            this.serverPrincipal = serverPrincipal;
        }

        @Override
        public byte[] generateToken(byte[] input, String authServer, Credentials credentials) {
            return Spnego.getToken(
                ((KeytabCredentials) credentials).getSubject(),
                serverPrincipal);
        }
    }

    public static class SpnegoLoginAuthenticator extends LoginAuthenticator {

        @Override
        public String getAuthMethod() {
            return Constraint.__SPNEGO_AUTH;
        }

        @Override
        public Authentication validateRequest(ServletRequest servletRequest, ServletResponse servletResponse, boolean mandatory) throws ServerAuthException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse res = (HttpServletResponse) servletResponse;

            String authzHeader = req.getHeader(HttpHeader.AUTHORIZATION.asString());
            String tokenEnc = getSpnegoToken(authzHeader);

            if (authzHeader != null && tokenEnc != null) {
                UserIdentity identity = _loginService.login(null, tokenEnc, req);
                if (identity != null) {
                    SpnegoUserPrincipal principal = (SpnegoUserPrincipal) identity.getUserPrincipal();
                    if (principal.getToken() != null)
                        setSpnegoToken(res, principal.getEncodedToken());
                    return new UserAuthentication(getAuthMethod(), identity);
                }
            }

            sendChallenge(res, null);
            return Authentication.SEND_CONTINUE;
        }

        private static void sendChallenge(HttpServletResponse response, String token) throws ServerAuthException {
            try {
                setSpnegoToken(response, token);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (IOException x) {
                throw new ServerAuthException(x);
            }
        }

        private static void setSpnegoToken(HttpServletResponse response, String token) {
            String value = HttpHeader.NEGOTIATE.asString();
            if (token != null) value += " " + token;
            response.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), value);
        }

        private static String getSpnegoToken(String header) {
            if (header == null)
                return null;
            String scheme = HttpHeader.NEGOTIATE.asString() + " ";
            if (header.regionMatches(true, 0, scheme, 0, scheme.length())) {
                return header.substring(scheme.length()).trim();
            }
            return null;
        }

        @Override
        public boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) {
            return true;
        }
    }

    public static class SpnegoLoginService implements LoginService {
        public static String REALM_HEADER = "x-google-realm";

        private IdentityService identityService;
        private SubjectProvider subjects;
        private String[] defaultRole;

        /**
         * @param principal  principal to use
         * @param keytabPath path to keytab file
         * @param role       user role name or null to use default
         */
        public SpnegoLoginService(String principal, String keytabPath, String role) throws LoginException {
            Subject subject = Spnego.login(principal, keytabPath, null, false);
            subjects = new StaticSubjectProvider(subject);
            if (role != null) {
                defaultRole = new String[]{role};
            } else {
                defaultRole = new String[]{"user"};
            }
        }

        @Override
        public String getName() {
            return this.getClass().getSimpleName();
        }

        /**
         * @param username
         * @param tokenEnc Base64 encoded SPNego token
         * @param request  HttpServletRequest
         * @return
         */
        @Override
        public UserIdentity login(String username, Object tokenEnc, ServletRequest request) {
            HttpServletRequest req = (HttpServletRequest) request;
            String realm = req.getHeader(REALM_HEADER);
            Spnego spnego = Spnego.acceptToken(subjects.getSubject(realm), (String) tokenEnc);

            if (spnego.isAuthenticated()) {
                SpnegoUserPrincipal user = new SpnegoUserPrincipal(spnego.getClientPrincipal(), spnego.getReturnToken());

                Subject subject = new Subject();
                subject.getPrincipals().add(user);

                return identityService.newUserIdentity(subject, user, defaultRole);
            }
            return null;
        }

        @Override
        public boolean validate(UserIdentity user) {
            return false;
        }

        @Override
        public IdentityService getIdentityService() {
            return identityService;
        }

        @Override
        public void setIdentityService(IdentityService service) {
            identityService = service;
        }

        @Override
        public void logout(UserIdentity user) {
        }
    }

    public static class MultiSubjectProvider implements SubjectProvider {
        private Map<String, Subject> subjects;
        private Subject defaultSubject;

        public MultiSubjectProvider(List<Subject> subjects, String defaultRealm) {
            if (defaultRealm == null) {
                throw new IllegalArgumentException("defaultRealm must not be null");
            }
            this.subjects = getByRealm(subjects);
            this.defaultSubject = this.subjects.get(defaultRealm);
            if (defaultSubject == null) {
                throw new IllegalArgumentException("Subject not found for default realm " + defaultRealm);
            }
        }

        @Override
        public Subject getSubject(String realm) {
            if (realm != null) {
                return subjects.get(realm);
            } else {
                return defaultSubject;
            }
        }
    }

    public interface SubjectProvider {

        /**
         * Retrieves logged in Subject for given realm
         *
         * @param realm realm or null to use default realm
         * @return Subject
         */
        Subject getSubject(String realm);
    }

    public static class StaticSubjectProvider implements SubjectProvider {
        private Subject subject;

        public StaticSubjectProvider(Subject subject) {
            this.subject = subject;
        }

        @Override
        public Subject getSubject(String realm) {
            return subject;
        }
    }



    /** Login as a single principal from a specific keytab
     *
     * @param principal principal name
     * @param keyTabPath keytab path
     * @param cache path to krb5 credential cache or null
     * @param initiator boolean indicating whether this is a client
     * @return logged in Subject
     * @throws LoginException
     */
    public static Subject login(String principal, String keyTabPath, String cache, boolean initiator) throws LoginException {
        LoginContext loginContext = new LoginContext("", new Subject(), null, getConfiguration(principal, keyTabPath, initiator, cache));
        loginContext.login();
        return loginContext.getSubject();
    }

    public static List<Subject> getSubjectsFromKeytab(Set<String> targetPrincipals, File keytabFile, boolean client, String krb5cc) throws LoginException {
        StringBuilder sb = new StringBuilder();
        targetPrincipals.forEach((s) -> sb.append(s + ","));
        if (!keytabFile.isFile() || !keytabFile.exists()) {
            throw new IllegalArgumentException("Keytab doesn't exist");
        }

        ArrayList<Subject> subjects = new ArrayList<>();
        for (String principal : targetPrincipals){
            try {
                Subject subject = login(principal, keytabFile.toString(), krb5cc, client);
                subjects.add(subject);
            } catch (LoginException e){
                //
            }
        }

        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("Principal not found in keytab");
        }
        return subjects;
    }

    public static Map<String, Subject> getByRealm(List<Subject> subjects) {
        Map<String, Subject> m = new HashMap<>();
        subjects.forEach((subj) -> {
            String realm = subj.getPrincipals(KerberosPrincipal.class).iterator().next().getRealm();
            if (m.containsKey(realm)) {
                throw new RuntimeException("Already exists for " + realm + " " + subj.getPrincipals().iterator().next().getName());
            } else {
                m.put(realm, subj);
            }
        });
        return ImmutableMap.copyOf(m);
    }

    public static SubjectProvider loadKeytabs(Set<String> principals, String keytabsPath, boolean client, String krb5cc, String defaultRealm) throws LoginException {
        List<Subject> subjects = loadKeytabs(principals, new File(keytabsPath), client, krb5cc);
        if (subjects.size() == 0) {
            throw new IllegalStateException("No valid Subject found in " + keytabsPath);
        } else if (subjects.size() == 1) {
            return new StaticSubjectProvider(subjects.get(0));
        } else {
            return new MultiSubjectProvider(subjects, defaultRealm);
        }
    }

    /**
     * Loads all Subjects found in a keytab or directory containing keytabs
     *
     * @param keytabsPath path to keytab or directory containing keytab files
     * @return
     */
    private static List<Subject> loadKeytabs(Set<String> principals, File keytabsPath, boolean client, String krb5cc) throws LoginException {
        ImmutableList.Builder<Subject> listBuilder = ImmutableList.builder();
        HashSet<String> set = new HashSet<>();
        File[] keytabFiles = new File[0];
        if (!keytabsPath.exists()) {
            throw new IllegalStateException("keytab path doesn't exist: " + keytabsPath);
        } else if (keytabsPath.isFile()) {
            keytabFiles = new File[]{keytabsPath};
        } else if (keytabsPath.isDirectory()) {
            keytabFiles = keytabsPath.listFiles();
            if (keytabFiles == null || keytabFiles.length < 1) {
                throw new IllegalStateException("No keytab found in path: " + keytabsPath);
            }
        }

        // Loop through the found files
        for (File keytabFile : keytabFiles) {
            if (keytabFile.isFile()) {
                if (keytabFile.getName().endsWith(".keytab")){
                    for (Subject subject : getSubjectsFromKeytab(principals, keytabFile, client, krb5cc)) {
                        String name = subject.getPrincipals().iterator().next().getName();
                        if (!set.contains(name)) {
                            set.add(name);
                            listBuilder.add(subject);
                        }
                    }
                }
            } else if (keytabFile.isDirectory()) {
                throw new RuntimeException("Keytab Subdirectory not allowed");
            }
        }

        List<Subject> subjects = listBuilder.build();
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("No subjects loaded from `" + keytabsPath.getAbsolutePath() + "`");
        }
        return subjects;
    }

    public static class StaticConfiguration extends Configuration {
        private final AppConfigurationEntry[] conf;

        public StaticConfiguration(Map<String, String> opts) {
            conf = new AppConfigurationEntry[]{
                new AppConfigurationEntry(Krb5LoginModule.class.getCanonicalName(), AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, opts)
            };
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            return conf;
        }
    }

    public static Map<String,String> getOptions(String principal, String keytab, boolean isInitiator, String ticketCache) {
        ImmutableMap.Builder<String, String> opts = ImmutableMap.builder();
        opts.put("principal", principal);
        opts.put("keyTab", keytab);
        opts.put("doNotPrompt", "true");
        opts.put("useKeyTab", "true");
        opts.put("storeKey", "true");
        opts.put("refreshKrb5Config", "true");
        opts.put("isInitiator", isInitiator ? "true" : "false");
        opts.put("renewTGT", "true");
        opts.put("useTicketCache", "true");
        if (ticketCache != null) {
            opts.put("ticketCache", new File(ticketCache).getAbsolutePath());
        }
        return opts.build();
    }

    public static Configuration getConfiguration(String principal, String keytab, boolean isInitiator, String ticketCache) {
        return new StaticConfiguration(getOptions(principal, keytab, isInitiator, ticketCache));
    }
}
