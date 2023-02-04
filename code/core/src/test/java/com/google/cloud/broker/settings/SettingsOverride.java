/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.broker.settings;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SettingsOverride implements TestRule, AutoCloseable {

  private Map<String, Object> newSettingsMap;
  private Config backupConfig;

  public SettingsOverride() {
    this(new HashMap<>());
  }

  public SettingsOverride(Map<String, Object> newSettingsMap) {
    this.newSettingsMap = newSettingsMap;
    // Keep a backup of the old settings
    backupConfig = AppSettings.getInstance();
  }

  /** Called when used as a jUnit Rule. */
  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        // Override the settings
        override();
        try {
          // Execute the test
          statement.evaluate();
        } finally {
          // Restore the old settings
          restore();
        }
      }
    };
  }

  private void override() {
    // Replace settings' instance with a new, temporary one
    AppSettings.setInstance(
        ConfigFactory.parseMap(newSettingsMap).withFallback(AppSettings.getInstance()));
  }

  private void restore() {
    // Restore the old settings
    AppSettings.setInstance(backupConfig);
  }

  /** To be used as an AutoCloseable in a try() clause. */
  public static SettingsOverride apply(Map<String, Object> newSettingsMap) {
    SettingsOverride settingsOverride = new SettingsOverride(newSettingsMap);
    settingsOverride.override();
    return settingsOverride;
  }

  /** Automatically triggered at the end of a try() clause. */
  @Override
  public void close() {
    restore();
  }
}
