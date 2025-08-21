package com.agent.model;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import lombok.Data;

/**
 * A simplified representation of a single API operation (e.g., a GET request to /users/{id}).
 * This class captures the essential details needed for the AI to understand and plan
 * how to call a specific endpoint.
 * <p>
 * Lombok's {@code @Data} annotation generates standard boilerplate code.
 */
@Data
public class ApiOperation {

    /**
     * A unique identifier for the operation, typically derived from the OpenAPI specification's
     * {@code operationId}.
     */
    private String operationId;

    /**
     * The HTTP method for this operation (e.g., "GET", "POST", "PUT", "DELETE").
     */
    private String httpMethod;

    /**
     * The URL path for the endpoint, which may include path parameters (e.g., "/users/{userId}").
     */
    private String path;

    /**
     * A human-readable description of what the operation does, taken from the OpenAPI specification.
     */
    private String description;

    /**
     * A list of parameters that this operation accepts.
     *
     * @see ApiParameter
     */
    private List<ApiParameter> parameters;

    /**
     * A list of security requirements for this operation, indicating which security schemes apply.
     */
    private List<SecurityRequirement> security;

    /**
     * The schema for the request body, if applicable (e.g., for POST or PUT requests).
     * This is stored as a generic {@code Object} to accommodate various schema definitions.
     */
    private Object requestBodySchema;
}