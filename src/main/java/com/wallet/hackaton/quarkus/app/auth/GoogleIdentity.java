package com.wallet.hackaton.quarkus.app.auth;

public record GoogleIdentity(String subject, String email, String name) {
}
