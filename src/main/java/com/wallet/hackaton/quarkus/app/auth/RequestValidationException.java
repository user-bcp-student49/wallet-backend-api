package com.wallet.hackaton.quarkus.app.auth;

public class RequestValidationException extends AuthProblemException {
    public RequestValidationException() {
        super(422, "Solicitud inválida", "VALIDATION_ERROR", "La solicitud no cumple el contrato.");
    }
}
