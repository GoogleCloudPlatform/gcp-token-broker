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

package com.google.cloud.broker.apps.brokerserver.accesstokens;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import org.powermock.api.mockito.PowerMockito;

public class MockAccessBoundary {

    public static void mock() {
        mockStatic(AccessBoundaryUtils.class);
        PowerMockito.when(AccessBoundaryUtils.addAccessBoundary(any(), anyString())).thenAnswer(invocation -> {
            AccessToken accessToken = (AccessToken) invocation.getArgument(0);
            String target = (String) invocation.getArgument(1);
            return new AccessToken(accessToken.getValue() + ";Target=" + target, accessToken.getExpiresAt());
        });
    }

}
