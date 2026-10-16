package com.wallet.hackaton.quarkus.app.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthSessionServiceTest {
    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final AuthSessionService service =
            new AuthSessionService(new MockGoogleIdentityProvider(), repository);

    @Test
    void createsAccountWithBiometricOnboardingPending() {
        var result = service.authenticate("mock-google-token-new-user");

        assertFalse(result.account().biometricRegistered());
        assertEquals("BIOMETRIC_PENDING", result.onboarding().status());
        assertEquals("REGISTER_BIOMETRIC", result.onboarding().nextAction());
    }

    @Test
    void resumesSameAccountForRepeatedIdentity() {
        var first = service.authenticate("mock-google-token-returning-user");
        var second = service.authenticate("mock-google-token-returning-user");

        assertEquals(first.account().id(), second.account().id());
    }

    @Test
    void rejectsInvalidIdentityToken() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate("invalid-google-token-value"));
    }
}
