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

package com.google.cloud.broker.client.hadoop.fs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;

import com.google.cloud.broker.client.connect.BrokerServerInfo;

public class Utils {

    public final static String CONFIG_ACCESS_BOUNDARY_ENABLED = "gcp.token.broker.access.boundary.enabled";
    public final static String CONFIG_URI = "gcp.token.broker.uri";
    public final static String CONFIG_PRINCIPAL = "gcp.token.broker.kerberos.principal";
    public final static String CONFIG_CERTIFICATE = "gcp.token.broker.tls.certificate";
    public final static String CONFIG_CERTIFICATE_PATH = "gcp.token.broker.tls.certificate.path";

    public static String getTarget(Configuration config, Text service) {
        boolean accessBoundaryEnabled = Boolean.parseBoolean(
            config.get(CONFIG_ACCESS_BOUNDARY_ENABLED, "false"));
        if (accessBoundaryEnabled) {
            String uri = service.toString();
            if (uri.startsWith("gs://")) {
                uri = "//storage.googleapis.com/projects/_/buckets/" + uri.substring(5);
            }
            return uri;
        }
        else {
            return "";
        }
    }

    public static BrokerServerInfo getBrokerDetailsFromConfig(Configuration config) {
        return new BrokerServerInfo(
            config.get(CONFIG_URI),
            config.get(CONFIG_PRINCIPAL),
            config.get(CONFIG_CERTIFICATE),
            config.get(CONFIG_CERTIFICATE_PATH)
        );
    }

}
