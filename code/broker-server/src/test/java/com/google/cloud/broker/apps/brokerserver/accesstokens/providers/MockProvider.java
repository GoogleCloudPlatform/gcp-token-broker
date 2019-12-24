package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;

/**
 * Mock provider only used for testing. Do NOT use in production!
 */
public class MockProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String googleIdentity, String scope) {
        return new AccessToken("FakeAccessToken/GoogleIdentity=" + googleIdentity + ";Scope=" + scope, 999999999L);
    }
}
