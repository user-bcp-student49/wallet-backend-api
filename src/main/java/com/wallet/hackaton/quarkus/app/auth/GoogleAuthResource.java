package com.wallet.hackaton.quarkus.app.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/auth/google")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GoogleAuthResource {
    private final AuthSessionService authSessionService;
    private final SessionTokenIssuer sessionTokenIssuer;

    @Inject
    public GoogleAuthResource(AuthSessionService authSessionService, SessionTokenIssuer sessionTokenIssuer) {
        this.authSessionService = authSessionService;
        this.sessionTokenIssuer = sessionTokenIssuer;
    }

    @POST
    public AuthSessionResponse authenticate(GoogleAuthRequest request) {
        if (request == null
                || request.idToken() == null
                || request.idToken().length() < 20
                || request.deviceId() == null
                || request.deviceId().isBlank()
                || request.deviceId().length() > 128) {
            throw new RequestValidationException();
        }

        AuthSessionService.AuthenticatedAccount authenticated =
                authSessionService.authenticate(request.idToken());
        Account account = authenticated.account();
        var onboarding = authenticated.onboarding();
        return new AuthSessionResponse(
                sessionTokenIssuer.issue(account.id()),
                SessionTokenIssuer.EXPIRES_IN_SECONDS,
                "Bearer",
                new AuthSessionResponse.User(
                        account.id(), account.email(), account.name(), account.biometricRegistered()),
                new AuthSessionResponse.Onboarding(
                        onboarding.status(), onboarding.nextAction(), onboarding.phoneNumberMasked()));
    }
}
