package com.dailyatelier.dailyatelier.service;

public class CloudinaryDeleteException extends RuntimeException {
    private final boolean retryable;

    public CloudinaryDeleteException(
            String message,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public CloudinaryDeleteException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
