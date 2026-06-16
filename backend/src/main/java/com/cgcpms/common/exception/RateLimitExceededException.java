package com.cgcpms.common.exception;

/**
 * Thrown when a rate-limited endpoint receives more requests than allowed.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
