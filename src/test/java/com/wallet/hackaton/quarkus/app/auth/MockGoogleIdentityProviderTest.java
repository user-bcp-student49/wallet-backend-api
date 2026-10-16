package com.wallet.hackaton.quarkus.app.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockGoogleIdentityProviderTest {
    private final MockGoogleIdentityProvider provider = new MockGoogleIdentityProvider();

    @Test
    void resolvesSameIdentityDeterministically() {
        String token = "mock-google-token-test-user";

        assertEquals(provider.resolve(token), provider.resolve(token));
    }

    @Test
    void rejectsTokensOutsideMockFormat() {
        assertTrue(provider.resolve("invalid-google-token-value").isEmpty());
    }
}
