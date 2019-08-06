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

package com.google.cloud.broker.settings;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppSettingsTest {

    @Test
    public void testRequireSetting() {
        try {
            AppSettings.requireSetting("xxxx");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("The `xxxx` setting is not set", e.getMessage());
        }
    }

    @Test
    public void testReset() {
        AppSettings.getInstance().setProperty("foo", "bar");
        assertEquals("bar", AppSettings.getInstance().getProperty("foo"));
        AppSettings.reset();
        assertEquals(null, AppSettings.getInstance().getProperty("foo"));
    }

}
