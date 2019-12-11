package com.google.cloud.broker.settings;

import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Utility class to temporarily override settings.
 * Useful mainly for testing.
 */
public class SettingsOverride implements AutoCloseable {

    private Config backup;

    public SettingsOverride(Map<String, Object> map) {
        // Keep a backup of the old settings
        backup = AppSettings.getInstance();

        // Override the settings
        AppSettings.setInstance(ConfigFactory.parseMap(map).withFallback(AppSettings.getInstance()));
    }

    @Override
    public void close() throws Exception {
        restore();
    }

    public void restore() throws Exception {
        // Restore the old settings
        AppSettings.setInstance(backup);
    }
}
