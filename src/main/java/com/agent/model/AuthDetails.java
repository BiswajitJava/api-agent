package com.agent.model;

/**
 * A simple, immutable data carrier for holding authentication details parsed
 * from a source like a Postman collection.
 * <p>
 * Using a {@code record} provides a concise way to declare this data structure
 * with automatically generated constructors, getters, {@code equals()},
 * {@code hashCode()}, and {@code toString()} methods.
 *
 * @param type The authentication type (e.g., "bearer", "apikey").
 * @param token The raw token or variable string (e.g., "secret-token" or "{{my_api_key}}").
 */
public record AuthDetails(String type, String token) {
}