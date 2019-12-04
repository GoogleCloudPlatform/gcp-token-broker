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
import com.typesafe.config.ConfigException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class AppSettingsTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testEnvironmentVariables() {
        // Check that the test variable doesn't exist in the environment yet
        try {
            AppSettings.getInstance().getString("FOO");
            fail();
        } catch (ConfigException.Missing e) {
            // Expected
        }

        // Set the test environment variable and reload the settings
        environmentVariables.set("APP_SETTING_FOO", "BAR");
        AppSettings.reset();

        // Check that the test setting has been loaded
        assertEquals("BAR", AppSettings.getInstance().getString("FOO"));

        // Remove the test environment variable and reload the settings
        environmentVariables.clear("APP_SETTING_FOO");
        AppSettings.reset();

        // Check that the test setting is gone
        try {
            AppSettings.getInstance().getString("FOO");
            fail();
        } catch (ConfigException.Missing e) {
            // Expected
        }
    }

}
