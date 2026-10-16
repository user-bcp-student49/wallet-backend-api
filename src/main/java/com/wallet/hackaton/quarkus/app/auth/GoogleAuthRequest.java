package com.wallet.hackaton.quarkus.app.auth;

public record GoogleAuthRequest(String idToken, String deviceId) {
}
