package com.google.cloud.broker.authentication.backends;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KerberosUtil {

    /**
     *
     * @param keytabsPath path of directory containing keytab files
     * @return
     */
    public static ArrayList<Subject> loadKeytabs(File keytabsPath, Set<String> serviceNames, Set<String> hostnames, Set<String> realms) {
        ArrayList<Subject> logins = new ArrayList<>();
        File[] keytabFiles = keytabsPath.listFiles();

        if (keytabFiles == null) {
            throw new IllegalStateException("Invalid path `" + keytabsPath + "` as defined in the `KEYTABS_PATH` setting");
        }

        if (keytabFiles.length > 0) {
            // Loop through the found files
            for (File keytabFile : keytabFiles) {
                if (keytabFile.isFile()) {
                    sun.security.krb5.internal.ktab.KeyTab keytab = sun.security.krb5.internal.ktab.KeyTab.getInstance(keytabFile);
                    sun.security.krb5.internal.ktab.KeyTabEntry[] entries = keytab.getEntries();

                    if (keytab.isValid() && entries.length > 0) {
                        // Perform further validation of entries in the keytab
                        for (sun.security.krb5.internal.ktab.KeyTabEntry entry : entries) {
                            String[] nameStrings = entry.getService().getNameStrings();

                            // Ensure that the entries' service name and hostname match the whitelisted values
                            String serviceName = nameStrings[0];
                            String hostname = nameStrings[1];
                            String realm = entry.getService().getRealmAsString();
                            if (!serviceNames.contains(serviceName)) {
                                throw new IllegalStateException(String.format("Invalid service name in keytab: %s", keytabFile.getPath()));
                            }
                            if (!hostnames.contains(hostname)) {
                                throw new IllegalStateException(String.format("Invalid hostname in keytab: %s", keytabFile.getPath()));
                            }
                            if (!realms.contains(realm)){
                                throw new IllegalStateException(String.format("Invalid realm in keytab: %s", keytabFile.getPath()));
                            }

                            String principal = serviceName + "/" + hostname + "@" + realm;
                            Subject subject = login(principal, keytabFile);
                            System.out.println("Logged in as " + principal + " from " + keytabFile.getAbsolutePath());
                            logins.add(subject);
                        }
                    }
                }
            }
        }

        return logins;
    }

    private static Subject login(String principal, File keytabFile) {
        try {
            LoginContext loginContext = new LoginContext(
                    "", new Subject(), null, getConfiguration(principal, keytabFile));
            loginContext.login();
            Subject subject = loginContext.getSubject();
            return subject;
        } catch (LoginException e) {
            throw new RuntimeException(e);
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

}
