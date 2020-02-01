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

package com.google.cloud.broker.usermapping;

import java.util.Map;

import static org.junit.Assert.*;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import org.junit.Test;

public class ShadowServiceAccountUserMapperTest {

    @Test
    public void testGoogleIdentity() throws Exception {
        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.SHADOW_PROJECT, "MY_SHADOW_PROJECT",
            AppSettings.SHADOW_USERNAME_PATTERN, "xxx-%s-XXX"
        ))) {

            ShadowServiceAccountUserMapper mapper = new ShadowServiceAccountUserMapper();
            assertEquals("xxx-alice-XXX@MY_SHADOW_PROJECT.iam.gserviceaccount.com", mapper.map("alice@EXAMPLE.COM"));
            assertEquals("xxx-alice-XXX@MY_SHADOW_PROJECT.iam.gserviceaccount.com", mapper.map("alice@EXAMPLE.NET"));
            assertEquals("xxx-alice-XXX@MY_SHADOW_PROJECT.iam.gserviceaccount.com", mapper.map("alice"));

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
