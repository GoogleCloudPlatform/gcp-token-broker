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

import com.google.cloud.broker.client.hadoop.fs.BrokerTokenIdentifier;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "GetSessionToken",
    description = "Retrieves a broker session token for delegated authentication"
)
public class GetSessionToken implements Runnable {

    @Option(names = {"-u", "--uri"}, required = true, description = "GCS URI for the access token")
    private String uri;

    @Option(names = {"-r", "--renewer"}, required = true, description = "Renewer for the session token")
    private String renewer;

    @Option(names = {"-f", "--session-token-file"}, required = true, description = "Output file for the session token")
    private String file;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help")
    boolean help;

    private void sendRequest(Configuration config) {
        String bucket = CommandUtils.extractBucketNameFromGcsUri(uri);
        Text service = new Text(bucket);
        UserGroupInformation ugi;
        try {
            ugi = UserGroupInformation.getCurrentUser();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String user = ugi.getUserName();
        Text owner = new Text(user);
        Text realUser = null;
        if (ugi.getRealUser() != null) {
            realUser = new Text(ugi.getRealUser().getUserName());
        }
        BrokerTokenIdentifier identifier = new BrokerTokenIdentifier(
            config,
            owner,
            new Text(renewer),
            realUser,
            service);
        String sessionToken = identifier.getSessionToken();
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(sessionToken);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("\n> Session token successfully written to:\n");
        System.out.println(file);
    }

    @Override
    public void run() {
        Configuration config = new Configuration();
        CommandUtils.showConfig(config);
        sendRequest(config);
    }

    public static void main(String[] args) {
        CommandLine.run(new GetSessionToken(), args);
    }

}
