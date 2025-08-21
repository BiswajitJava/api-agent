package com.agent.exception;

/**
 * A custom runtime exception for application-specific errors within the API Agent.
 * <p>
 * This exception is used to wrap and signal errors that occur during the application's
 * logical flow, such as failures in planning, execution, or state management. It allows
 * for more specific error handling compared to generic runtime exceptions.
 */
public class ApiAgentException extends RuntimeException {

    /**
     * Constructs a new ApiAgentException with the specified detail message.
     *
     * @param message The detail message, which is saved for later retrieval by the
     *                {@link #getMessage()} method.
     */
    public ApiAgentException(String message) {
        super(message);
    }

    /**
     * Constructs a new ApiAgentException with the specified detail message and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is not automatically
     * incorporated into this exception's detail message.
     *
     * @param message The detail message (which is saved for later retrieval by the
     *                {@link #getMessage()} method).
     * @param cause   The cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). A {@code null} value is permitted,
     *                and indicates that the cause is nonexistent or unknown.
     */
    public ApiAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}