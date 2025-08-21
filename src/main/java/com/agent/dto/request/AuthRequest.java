package com.agent.dto.request;

/**
 * A record that encapsulates the data required to configure authentication for an API.
 * This is an immutable data carrier object used in the authentication command flow.
 *
 * @param alias The unique alias identifying the API to be authenticated.
 * @param type  The type of authentication mechanism (e.g., "api_key").
 * @param token The actual credential or token string.
 */
public record AuthRequest(String alias, String type, String token) {
}