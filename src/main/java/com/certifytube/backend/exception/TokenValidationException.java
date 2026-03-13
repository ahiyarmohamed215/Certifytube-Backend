package com.certifytube.backend.exception;

import lombok.Getter;

@Getter
public class TokenValidationException extends RuntimeException {
    private final String code;

    public TokenValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
