package com.wallet.hackaton.quarkus.app.auth;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class MockGoogleIdentityProvider implements IdentityProvider {
    private static final String TOKEN_PREFIX = "mock-google-token-";

    @Override
    public Optional<GoogleIdentity> resolve(String idToken) {
        if (idToken == null || !idToken.startsWith(TOKEN_PREFIX)
                || idToken.length() == TOKEN_PREFIX.length()) {
            return Optional.empty();
        }

        String subject = digest(idToken);
        return Optional.of(new GoogleIdentity(
                subject,
                "user-" + subject.substring(0, 16) + "@example.test",
                "Mock User " + subject.substring(0, 8)));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
