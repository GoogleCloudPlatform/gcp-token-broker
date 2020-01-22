package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.util.Collection;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;

/**
 * Mock provider only used for testing. Do NOT use in production!
 */
public class MockProvider extends AbstractProvider {

    @Override
    public AccessToken getAccessToken(String owner, Collection<String> scopes) {
        return new AccessToken(
            "FakeAccessToken/Owner=" + owner.toLowerCase() + ";Scopes=" + String.join(",", scopes),
            999999999L);

}
