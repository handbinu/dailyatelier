package com.dailyatelier.dailyatelier.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PointApiException extends IllegalStateException {
    private final HttpStatus status;
    private final String code;

    public PointApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
