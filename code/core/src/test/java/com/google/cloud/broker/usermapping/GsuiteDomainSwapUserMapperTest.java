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

package com.google.cloud.broker.usermapping;

import java.util.Map;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import org.junit.Test;
import static org.junit.Assert.*;

public class GsuiteDomainSwapUserMapperTest {

    @Test
    public void testGoogleIdentity() throws Exception {
        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.GSUITE_DOMAIN, "example.com"
        ))) {
            GsuiteDomainSwapUserMapper mapper = new GsuiteDomainSwapUserMapper();
            assertEquals("alice@example.com", mapper.map("alice@EXAMPLE.COM"));
            assertEquals("alice@example.com", mapper.map("alice@EXAMPLE.NET"));
            assertEquals("alice@example.com", mapper.map("alice"));
            try {
                mapper.map("");
                fail("IllegalArgumentException not thrown");
            } catch (IllegalArgumentException e) {
            }

            try {
                mapper.map("@EXAMPLE.NET");
                fail("IllegalArgumentException not thrown");
            } catch (IllegalArgumentException e) {
            }

            try {
                mapper.map("@");
                fail("IllegalArgumentException not thrown");
            } catch (IllegalArgumentException e) {
            }
        }
    }

}
