package com.dailyatelier.dailyatelier.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DomainApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public DomainApiException(
            HttpStatus status,
            String code,
            String message,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}
