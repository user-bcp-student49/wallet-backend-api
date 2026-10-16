package com.wallet.hackaton.quarkus.app.auth;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ProblemExceptionMapper implements ExceptionMapper<AuthProblemException> {
    @Override
    public Response toResponse(AuthProblemException exception) {
        Problem problem = new Problem(
                "about:blank", exception.title(), exception.status(), exception.code(), exception.detail());
        return Response.status(exception.status())
                .type("application/problem+json")
                .entity(problem)
                .build();
    }
}
