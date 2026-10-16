package com.wallet.hackaton.quarkus.app.auth;

public record Problem(String type, String title, int status, String code, String detail) {
}
