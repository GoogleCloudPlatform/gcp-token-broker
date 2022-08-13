// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.client.hadoop.fs.commands;

import com.google.cloud.broker.client.hadoop.fs.BrokerAccessTokenProvider;
import com.google.cloud.broker.client.hadoop.fs.BrokerTokenIdentifier;
import com.google.cloud.broker.client.hadoop.fs.BrokerTokenRenewer;
import com.google.cloud.broker.client.hadoop.fs.Utils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;

/**
 * This command runs several checks to verify that the Hadoop client is correctly configured
 * to access the broker server endpoints.
 *
 * To run it:
 *
 *   java -cp $(hadoop classpath) com.google.cloud.broker.client.hadoop.fs.commands.PingServer
 *
 */

public class PingServer {

    private final static String BUCKET = "gs://example";
    private final static Text SERVICE = new Text(BUCKET);
    private final static String CHECK_SUCCESS = "✅ Check successful\n";
    private final static String CHECK_FAIL = "\uD83D\uDED1 Check failed\n";

    private static void checkProviderDirectAuth(Configuration config) {
        System.out.println("Checking direct authentication...\n");
        try {
            BrokerAccessTokenProvider provider = new BrokerAccessTokenProvider(SERVICE);
            provider.setConf(config);
            provider.refresh();
            assert provider.getAccessToken().getToken().startsWith("ya29.");
            System.out.println(CHECK_SUCCESS);
        } catch(Exception e) {
            System.out.println(CHECK_FAIL);
            e.printStackTrace(System.out);
            System.out.println();
        }
    }

    private static void checkProviderDelegatedAuth(Configuration config, String sessionToken) {
        System.out.println("Checking delegated authentication...\n");
        try {
            BrokerAccessTokenProvider provider = new BrokerAccessTokenProvider(SERVICE, CommandUtils.getBTI(sessionToken));
            provider.setConf(config);
            provider.refresh();
            assert provider.getAccessToken().getToken().startsWith("ya29.");
            System.out.println(CHECK_SUCCESS);
        } catch(Exception e) {
            System.out.println(CHECK_FAIL);
            e.printStackTrace(System.out);
            System.out.println();
        }
    }

    private static String checkGetSessionToken(Configuration config) {
        try {
            UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
            Text username = new Text(loginUser.getUserName());
            BrokerTokenIdentifier identifier = new BrokerTokenIdentifier(config, username, username, username, SERVICE);
            String sessionToken = identifier.getSessionToken();
            assert (sessionToken.length() > 0);
            System.out.println(CHECK_SUCCESS);
            return sessionToken;
        } catch (Exception e) {
            System.out.println(CHECK_FAIL);
            e.printStackTrace(System.out);
            System.out.println();
            return null;
        }
    }

    private static void checkRenewSessionToken(Configuration config, String sessionToken) throws IOException {
        try {
            Token<BrokerTokenIdentifier> token = CommandUtils.getTokenBTI(sessionToken, SERVICE);
            BrokerTokenRenewer renewer = new BrokerTokenRenewer();
            renewer.renew(token, config);
            System.out.println(CHECK_SUCCESS);
        } catch (Exception e) {
            System.out.println(CHECK_FAIL);
            e.printStackTrace(System.out);
            System.out.println();
        }
    }

    private static void checkCancelSessionToken(Configuration config, String sessionToken) throws IOException {
        try {
            Token<BrokerTokenIdentifier> token = CommandUtils.getTokenBTI(sessionToken, SERVICE);
            BrokerTokenRenewer renewer = new BrokerTokenRenewer();
            renewer.cancel(token, config);
            System.out.println(CHECK_SUCCESS);
        } catch (Exception e) {
            System.out.println(CHECK_FAIL);
            e.printStackTrace(System.out);
            System.out.println();
        }
    }

    public static void main(String[] args) throws IOException {
        Configuration config = new Configuration();
        CommandUtils.showConfig(config);

        System.out.println("\n> Checking the broker server endpoints:\n");

        System.out.println("===> Checking GetSessionToken...\n");
        String sessionToken = checkGetSessionToken(config);

        System.out.println("===> Checking GetAccessToken...\n");
        checkProviderDirectAuth(config);

        if (sessionToken != null) {
            checkProviderDelegatedAuth(config, sessionToken);

            System.out.println("===> Checking RenewAccessToken...\n");
            checkRenewSessionToken(config, sessionToken);

            System.out.println("===> Checking CancelAccessToken...\n");
            checkCancelSessionToken(config, sessionToken);
        }
        else {
            System.out.println(
                "⚠️  Skipping checks for GetAccessToken (delegated auth), RenewAccessToken, and CancelAccessToken " +
                "because the call to GetSessionToken returned an error. Please try again later.");
        }
    }

}
