package com.wallet.hackaton.quarkus.app.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class SessionTokenIssuer {
    public static final int EXPIRES_IN_SECONDS = 900;
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final byte[] signingKey;

    public SessionTokenIssuer(
            @ConfigProperty(name = "wallet.auth.jwt.secret") String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException("wallet.auth.jwt.secret must be configured");
        }
        this.signingKey = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 32) {
            throw new IllegalStateException("wallet.auth.jwt.secret must contain at least 32 bytes");
        }
    }

    public String issue(String userId) {
        long issuedAt = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + userId + "\",\"iat\":" + issuedAt
                + ",\"exp\":" + (issuedAt + EXPIRES_IN_SECONDS) + "}");
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(signingKey, HMAC_SHA_256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign the session token", exception);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
