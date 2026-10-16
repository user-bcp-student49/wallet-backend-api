package com.wallet.hackaton.quarkus.app.auth;

public interface AccountRepository {
    Account findOrCreate(String provider, GoogleIdentity identity);
}
