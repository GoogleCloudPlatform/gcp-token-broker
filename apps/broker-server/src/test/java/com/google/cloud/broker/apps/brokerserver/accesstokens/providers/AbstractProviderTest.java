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

import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.settings.AppSettings;


public class AbstractProviderTest {

    @Test
    public void testGetInstance() throws Exception {
        try(SettingsOverride override = new SettingsOverride(Map.of(AppSettings.PROVIDER_BACKEND, "com.example.DoesNotExist"))) {
            try {
                AbstractProvider.getInstance();
                fail();
            } catch (RuntimeException e) {
                assertEquals("java.lang.ClassNotFoundException: com.example.DoesNotExist", e.getMessage());
            }
        }
    }

}
