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

package com.google.cloud.broker.authentication.backends;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.login.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.apache.hadoop.security.authentication.util.KerberosName;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;


public class SpnegoAuthenticator extends AbstractAuthenticationBackend {

    private ArrayList<Subject> logins = new ArrayList<Subject>();
    private final static String INVALID_SETTING = "Invalid `" + AppSettings.KEYTABS + "` setting";
    private List<Rule> rulesList = new ArrayList<Rule>();

    public SpnegoAuthenticator() {
        KerberosName.setRules(AppSettings.getInstance().getString(AppSettings.KERBEROS_NAME_TRANSLATION_RULES));
        KerberosName.setRuleMechanism(KerberosName.MECHANISM_MIT);
        parseShortNameRules(AppSettings.getInstance().getString(AppSettings.SHORTNAME_TRANSLATION_RULES));
    }

    private void initLogin() {
        // Parse the keytabs setting
        List<? extends Config> keytabs = AppSettings.getInstance().getConfigList(AppSettings.KEYTABS);

        // Log in each individual principal
        for (Config item : keytabs) {
            String principal;
            String keytab;
            try {
                principal = item.getString("principal");
                keytab = item.getString("keytab");
            } catch (ConfigException e) {
                throw new IllegalArgumentException(INVALID_SETTING + " -- Error: " + e.getMessage());
            }


            File keytabFile = new File(keytab);
            if (!keytabFile.exists()) {
                throw new IllegalArgumentException("Keytab `" + keytab + "` in `" + AppSettings.KEYTABS + "` setting does not exist");
            }

            Subject subject = principalLogin(principal, keytabFile);
            logins.add(subject);
        }

        if (logins.size() == 0) {
            throw new IllegalArgumentException(INVALID_SETTING);
        }
    }



    private Subject principalLogin(String principal, File keytabFile) {
        try {
            LoginContext loginContext = new LoginContext(
                    "", new Subject(), null, getConfiguration(principal, keytabFile));
            loginContext.login();
            return loginContext.getSubject();
        } catch (LoginException e) {
            throw new RuntimeException("Failed login for principal `" + principal + "` with keytab `" + keytabFile.getPath() + "`. Error message: " + e.getMessage());
        }
    }


    private static Configuration getConfiguration(String principal, File keytabFile) {
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                Map<String, String> options = new HashMap<String, String>();
                options.put("principal", principal);
                options.put("keyTab", keytabFile.getPath());
                options.put("doNotPrompt", "true");
                options.put("useKeyTab", "true");
                options.put("storeKey", "true");
                options.put("isInitiator", "false");
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
                };
            }
        };
    }


    public String authenticateUser(String authorizationHeader) {
        if (logins.isEmpty()) {
            initLogin();
        }

        if (! authorizationHeader.startsWith("Negotiate ")) {
            throw Status.UNAUTHENTICATED.withDescription("Use \"authorization: Negotiate <token>\" metadata to authenticate").asRuntimeException();
        }

        String spnegoToken = authorizationHeader.split("\\s")[1];

        String authenticatedUser = null;

        for (Subject login : logins) {
            authenticatedUser = Subject.doAs(login, (PrivilegedAction<String>) () -> {
                try {
                    GSSManager manager = GSSManager.getInstance();
                    Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
                    GSSCredential serverCredential = manager.createCredential(null,
                        GSSCredential.DEFAULT_LIFETIME,
                        spnegoOid,
                        GSSCredential.ACCEPT_ONLY);
                    GSSContext context = manager.createContext(serverCredential);
                    byte[] tokenBytes = Base64.getDecoder().decode(spnegoToken.getBytes());
                    context.acceptSecContext(tokenBytes, 0, tokenBytes.length);
                    return context.getSrcName().toString();
                } catch (GSSException e) {
                    return null;
                }
            });
            if (authenticatedUser != null) {
                return authenticatedUser;
            }
        }

        throw Status.UNAUTHENTICATED.withDescription("SPNEGO authentication failed").asRuntimeException();
    }

    private static class Rule {
        private final Pattern match;
        private final Pattern fromPattern;
        private final String toPattern;
        private final boolean toLowerCase;

        Rule(String match, String fromPattern,
             String toPattern, boolean toLowerCase) {
            this.match = match == null ? null : Pattern.compile(match);
            this.fromPattern =
                fromPattern == null ? null : Pattern.compile(fromPattern);
            this.toPattern = toPattern;
            this.toLowerCase = toLowerCase;
        }

        String apply(String shortName) {
            String result = null;
            if (match == null || match.matcher(shortName).matches()) {
                if (fromPattern == null) {
                    result = shortName;
                } else {
                    Matcher fromMatcher = fromPattern.matcher(shortName);
                    result = fromMatcher.replaceFirst(toPattern);
                }
            }
            if (toLowerCase && result != null) {
                result = result.toLowerCase(Locale.ENGLISH);
            }
            return result;
        }
    }

    private static final Pattern ruleParser = Pattern.compile("\\s*(RULE:(\\(([^)]*)\\))?(s/([^/]*)/([^/]*))?)/?(L)?");


    private void parseShortNameRules(String rules) {
        rules = rules.trim();
        while (rules.length() > 0) {
            Matcher matcher = ruleParser.matcher(rules);
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("Invalid rule: " + rules);
            }
            rulesList.add(new Rule(
                matcher.group(3),
                matcher.group(5),
                matcher.group(6),
                "L".equals(matcher.group(7))));
            rules = rules.substring(matcher.end());
        }
    }

    public String translateShortName(String shortName) {
        for (Rule rule: rulesList) {
            String translated = rule.apply(shortName);
            if (translated != null) {
                return translated;
            }
        }
        throw new IllegalArgumentException("Principal `" + shortName + "` cannot be matched to a Google identity.");
    }

    public String translateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        else if (name.contains("@")) {
            KerberosName kn = new KerberosName(name);
            try {
                String translated = kn.getShortName();
                if (name.equals(translated)) {
                    throw new IllegalArgumentException("Principal `" + name + "` cannot be matched to a Google identity.");
                }
                return translated;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            // If it's not a Kerberos name, then use the "short name" rule instead.
            return translateShortName(name);
        }
    }

}