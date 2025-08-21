package com.agent.dto.request;

/**
 * A record that encapsulates the data required to "learn" an API from its specification.
 * This is an immutable data carrier object used in the API learning command flow.
 *
 * @param alias  A user-defined, unique alias to identify the API in the system.
 * @param source The URL or local file path of the OpenAPI specification.
 */
public record LearnApiRequest(String alias, String source) {
}