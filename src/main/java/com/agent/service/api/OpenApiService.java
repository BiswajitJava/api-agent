package com.agent.service.api;

import com.agent.model.ApiSpecification;

public interface OpenApiService {
    /**
     * Loads and parses an OpenAPI specification from a given source URL or file path.
     * @param source The URL or local file path of the OpenAPI specification.
     * @return A structured, internal representation of the API specification.
     */
    ApiSpecification loadAndParseSpec(String source);
}