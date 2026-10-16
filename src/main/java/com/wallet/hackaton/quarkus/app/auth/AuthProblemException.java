package com.wallet.hackaton.quarkus.app.auth;

public abstract class AuthProblemException extends RuntimeException {
    private final int status;
    private final String title;
    private final String code;
    private final String detail;

    protected AuthProblemException(int status, String title, String code, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
        this.code = code;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
