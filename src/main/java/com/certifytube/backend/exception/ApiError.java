package com.certifytube.backend.exception;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;

    public ApiError() {}

    public ApiError(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, null, message, path);
    }

    public ApiError(Instant timestamp, int status, String error, String code, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.path = path;
    }

}
