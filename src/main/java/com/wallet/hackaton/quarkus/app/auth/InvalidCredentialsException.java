package com.wallet.hackaton.quarkus.app.auth;

public class InvalidCredentialsException extends AuthProblemException {
    public InvalidCredentialsException() {
        super(401, "No autorizado", "INVALID_CREDENTIALS", "No se pudo validar la identidad.");
    }
}
