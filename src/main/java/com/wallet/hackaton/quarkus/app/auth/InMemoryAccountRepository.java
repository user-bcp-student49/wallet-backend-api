package com.wallet.hackaton.quarkus.app.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class InMemoryAccountRepository implements AccountRepository {
    private final ConcurrentMap<IdentityKey, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Account findOrCreate(String provider, GoogleIdentity identity) {
        // Email is profile data; linking accounts by email is outside this flow.
        var key = new IdentityKey(provider, identity.subject());
        return accounts.computeIfAbsent(key, ignored ->
                Account.newGoogleAccount(identity.subject(), identity.email(), identity.name()));
    }

    private record IdentityKey(String provider, String subject) {
    }
}
