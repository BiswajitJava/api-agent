package com.agent.model;

import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * A simplified, curated representation of an entire OpenAPI specification.
 * This model extracts only the essential information required for the AI planning and
 * execution engine, making it easier to process than the full OpenAPI object model.
 * <p>
 * Lombok's {@code @Data} annotation generates standard boilerplate code.
 */
@Data
public class ApiSpecification {

    /**
     * A map of all available operations in the API, keyed by their unique {@code operationId}.
     */
    private Map<String, ApiOperation> operations;

    /**
     * A map of security schemes defined in the API specification, keyed by their names.
     * These schemes define how authentication and authorization are handled.
     */
    private Map<String, SecurityScheme> securitySchemes;

    /**
     * A list of base server URLs for the API. The execution engine will use these
     * to construct the full request URLs.
     */
    private List<String> serverUrls;
}