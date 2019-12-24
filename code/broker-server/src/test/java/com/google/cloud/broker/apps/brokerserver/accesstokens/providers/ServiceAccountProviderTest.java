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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import static org.junit.Assert.*;
import org.junit.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;


public class ServiceAccountProviderTest {

    private static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
    String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);;

    @Test
    public void testSuccess() {
        ServiceAccountProvider provider = new ServiceAccountProvider();
        AccessToken accessToken = provider.getAccessToken("alice-svc-acct@" + projectId + ".iam.gserviceaccount.com", SCOPE);
        assertTrue(accessToken.getValue().length() > 0);
        assertTrue(accessToken.getExpiresAt() > 0);
    }

    @Test
    public void testInvalidIdentity() {
        ServiceAccountProvider provider = new ServiceAccountProvider();
        try {
            provider.getAccessToken("", SCOPE);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            provider.getAccessToken("alice@altostrat.com", SCOPE);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testUnauthorized() {
        ServiceAccountProvider provider = new ServiceAccountProvider();
        try {
            provider.getAccessToken("bob-svc-acct@" + projectId + ".iam.gserviceaccount.com", SCOPE);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
        }
    }

}
