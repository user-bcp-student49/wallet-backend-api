package com.wallet.hackaton.quarkus.app.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.function.Supplier;

@ApplicationScoped
public class AuthSessionService {
    private final Supplier<IdentityProvider> identityProvider;
    private final AccountRepository accountRepository;

    @Inject
    public AuthSessionService(Instance<IdentityProvider> identityProvider, AccountRepository accountRepository) {
        this.identityProvider = identityProvider::get;
        this.accountRepository = accountRepository;
    }

    AuthSessionService(IdentityProvider identityProvider, AccountRepository accountRepository) {
        this(() -> identityProvider, accountRepository);
    }

    private AuthSessionService(Supplier<IdentityProvider> identityProvider, AccountRepository accountRepository) {
        this.identityProvider = identityProvider;
        this.accountRepository = accountRepository;
    }

    public AuthenticatedAccount authenticate(String idToken) {
        GoogleIdentity identity = identityProvider.get().resolve(idToken)
                .orElseThrow(InvalidCredentialsException::new);
        Account account = accountRepository.findOrCreate("google", identity);
        return new AuthenticatedAccount(
                account,
                new OnboardingState(
                        account.onboardingStatus(),
                        account.nextAction(),
                        account.phoneNumberMasked()));
    }

    public record AuthenticatedAccount(Account account, OnboardingState onboarding) {
    }

    public record OnboardingState(String status, String nextAction, String phoneNumberMasked) {
    }
}
