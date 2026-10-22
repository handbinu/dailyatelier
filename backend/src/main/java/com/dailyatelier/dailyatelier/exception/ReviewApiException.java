package com.dailyatelier.dailyatelier.exception;

import org.springframework.http.HttpStatus;

public class ReviewApiException extends DomainApiException {
    public ReviewApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
