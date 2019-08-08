package com.google.cloud.broker.accesstokens.providers;

import com.google.cloud.broker.accesstokens.AccessToken;

/**
 * Mock provider only used for testing. Do NOT use in production!
 */
public class MockProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String owner, String scope) {
        return new AccessToken("FakeAccessToken/Owner=" + owner.toLowerCase() + ";Scope=" + scope, 999999999L);
    }
}
