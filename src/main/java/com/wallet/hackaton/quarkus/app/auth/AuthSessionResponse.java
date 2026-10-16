package com.wallet.hackaton.quarkus.app.auth;

public record AuthSessionResponse(
        String accessToken,
        int expiresIn,
        String tokenType,
        User user,
        Onboarding onboarding) {

    public record User(String id, String email, String name, boolean biometricRegistered) {
    }

    public record Onboarding(String status, String nextAction, String phoneNumberMasked) {
    }
}
