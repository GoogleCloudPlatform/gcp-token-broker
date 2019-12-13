package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;

/**
 * Mock provider only used for testing. Do NOT use in production!
 */
public class MockProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String owner, String scope, String target) {
        return new AccessToken(
            "FakeAccessToken/Owner=" + owner.toLowerCase() + ";" + "Scope=" + scope + ";" + "Target=" + target,
            999999999L
        );
    }
}
