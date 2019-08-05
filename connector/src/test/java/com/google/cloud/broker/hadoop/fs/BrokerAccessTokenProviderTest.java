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

package com.google.cloud.broker.hadoop.fs;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

import static com.google.cloud.broker.hadoop.fs.SpnegoUtilsTest.TGT_ERROR;

public class BrokerAccessTokenProviderTest {

    public void refresh(Configuration conf) throws IOException {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        BrokerAccessTokenProvider provider = (BrokerAccessTokenProvider) binding.deployUnbonded();
        provider.setConf(conf);
        provider.refresh();
    }

    @Test
    public void testProviderRefreshWhileNotLoggedIn() {
        try {
            Configuration conf = new Configuration();
            refresh(conf);
            fail();
        } catch (Exception e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertEquals(
                "User is not logged-in with Kerberos or cannot authenticate with the broker. Kerberos error message: " + TGT_ERROR,
                e.getMessage()
            );
        }
    }
}
