package com.wallet.hackaton.quarkus.app.auth;

import java.util.Optional;

public interface IdentityProvider {
    Optional<GoogleIdentity> resolve(String idToken);
}
