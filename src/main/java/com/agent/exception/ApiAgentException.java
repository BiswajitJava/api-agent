package com.agent.exception;

/**
 * Custom exception for application-specific errors.
 */
public class ApiAgentException extends RuntimeException {

    public ApiAgentException(String message) {
        super(message);
    }

    public ApiAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}