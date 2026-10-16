package com.wallet.hackaton.quarkus.app.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokenIssuerTest {
    private static final String TEST_SECRET = "test-only-signing-secret-for-quarkus-tests-32-bytes-minimum";

    @Test
    void issuesSignedJwtWithFifteenMinuteExpiry() {
        String token = new SessionTokenIssuer(TEST_SECRET).issue("user-123");
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Matcher claims = Pattern.compile("\"sub\":\"user-123\",\"iat\":(\\d+),\"exp\":(\\d+)")
                .matcher(payload);

        assertEquals(3, parts.length);
        assertTrue(claims.find());
        assertEquals(900, Long.parseLong(claims.group(2)) - Long.parseLong(claims.group(1)));
    }

    @Test
    void rejectsMissingOrWeakSigningSecret() {
        assertThrows(IllegalStateException.class, () -> new SessionTokenIssuer(""));
        assertThrows(IllegalStateException.class, () -> new SessionTokenIssuer("short"));
    }
}
