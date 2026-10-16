package com.wallet.hackaton.quarkus.app.auth;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class IdentityProviderStartupCheck {
    private final Instance<IdentityProvider> identityProvider;

    @Inject
    public IdentityProviderStartupCheck(Instance<IdentityProvider> identityProvider) {
        this.identityProvider = identityProvider;
    }

    void verifyProvider(@Observes StartupEvent event) {
        if (identityProvider.isUnsatisfied()) {
            throw new IllegalStateException("No identity provider is configured for this profile");
        }
    }
}
