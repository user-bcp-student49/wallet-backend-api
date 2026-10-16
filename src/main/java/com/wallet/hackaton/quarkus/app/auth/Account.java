package com.wallet.hackaton.quarkus.app.auth;

public record Account(
        String id,
        String provider,
        String subject,
        String email,
        String name,
        boolean biometricRegistered,
        String onboardingStatus,
        String nextAction,
        String phoneNumberMasked) {

    static Account newGoogleAccount(String subject, String email, String name) {
        return new Account(
                java.util.UUID.randomUUID().toString(),
                "google",
                subject,
                email,
                name,
                false,
                "BIOMETRIC_PENDING",
                "REGISTER_BIOMETRIC",
                null);
    }
}
