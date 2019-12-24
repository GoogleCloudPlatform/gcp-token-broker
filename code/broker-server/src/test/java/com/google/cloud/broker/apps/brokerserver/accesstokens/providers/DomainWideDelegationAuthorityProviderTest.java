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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Test;

public class DomainWideDelegationAuthorityProviderTest {

    // TODO: Still needs tests:
    // - Happy path

    private static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";

    @Test
    public void testUnauthorized() {
        DomainWideDelegationAuthorityProvider provider = new DomainWideDelegationAuthorityProvider();
        try {
            provider.getAccessToken("bob@EXAMPLE.COM", SCOPE);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
        }
    }

}
