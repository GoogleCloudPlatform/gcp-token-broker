package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.util.List;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;

/**
 * Mock provider only used for testing. Do NOT use in production!
 */
public class MockProvider extends AbstractUserProvider {

    @Override
    public AccessToken getAccessToken(String googleIdentity, List<String> scopes) {
        return new AccessToken(
            "FakeAccessToken/GoogleIdentity=" + googleIdentity + ";Scopes=" + String.join(",", scopes),
            999999999L);
    }

}
