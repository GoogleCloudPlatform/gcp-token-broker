package com.google.cloud.broker.authentication.backends;

import com.google.cloud.broker.settings.AppSettings;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

import java.util.Set;

public class SpnegoAuthenticatorTest {

    @Test
    public void test() {
        AppSettings.reset();
        AppSettings.setProperty(SpnegoAuthenticator.BROKER_SERVICE_NAME,"broker,tb");
        AppSettings.setProperty(SpnegoAuthenticator.BROKER_SERVICE_HOSTNAME,"lb.example.com,localhost");
        Config config = AppSettings.getConfig();
        Set<String> serviceNames = ImmutableSet.copyOf(config.getString(SpnegoAuthenticator.BROKER_SERVICE_NAME).split(","));
        Set<String> hostNames = ImmutableSet.copyOf(config.getString(SpnegoAuthenticator.BROKER_SERVICE_HOSTNAME).split(","));
        assertTrue(serviceNames.contains("broker"));
        assertTrue(serviceNames.contains("tb"));
        assertTrue(hostNames.contains("lb.example.com"));
        assertTrue(hostNames.contains("localhost"));
    }
}
