package com.agent.service.impl;

import com.agent.model.ApiOperation;
import com.agent.model.ApiParameter;
import com.agent.model.ApiSpecification;
import com.agent.service.api.OpenApiService;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * An implementation of the {@link OpenApiService} that uses the Swagger Parser library
 * to load and transform an OpenAPI specification into a simplified, internal model.
 * <p>
 * This service handles the complexity of parsing the specification and extracts only the
 * essential information required for the AI planning and execution components.
 */
@Service
public class OpenApiServiceImpl implements OpenApiService {

    /**
     * Loads an OpenAPI specification from a given source (URL or file path) and parses it
     * into a simplified {@link ApiSpecification} object.
     * <p>
     * This method extracts server URLs, global security schemes, and a flattened list of
     * operations. For each operation, it captures critical details like its ID, method,
     * path, description, parameters, and security requirements. If an operation lacks an
     * explicit {@code operationId}, a deterministic one is generated based on its
     * HTTP method and path.
     *
     * @param source The URL or local file path of the OpenAPI specification.
     * @return A simplified {@link ApiSpecification} instance.
     * @throws IllegalArgumentException if the specification cannot be parsed or found at the source.
     */
    @Override
    public ApiSpecification loadAndParseSpec(String source) {
        OpenAPI openAPI = new OpenAPIParser().readLocation(source, null, null).getOpenAPI();
        if (openAPI == null) {
            throw new IllegalArgumentException("Failed to parse or find OpenAPI spec from source: " + source);
        }

        ApiSpecification spec = new ApiSpecification();

        spec.setServerUrls(
                Optional.ofNullable(openAPI.getServers())
                        .orElse(Collections.emptyList())
                        .stream()
                        .map(Server::getUrl)
                        .collect(Collectors.toList())
        );

        spec.setSecuritySchemes(
                Optional.ofNullable(openAPI.getComponents())
                        .map(Components::getSecuritySchemes)
                        .orElse(Collections.emptyMap())
        );

        spec.setOperations(openAPI.getPaths().entrySet().stream()
                .flatMap(pathEntry -> pathEntry.getValue().readOperationsMap().entrySet().stream()
                        .map(opEntry -> {
                            Operation operation = opEntry.getValue();
                            ApiOperation apiOperation = new ApiOperation();

                            String operationId = operation.getOperationId() != null
                                    ? operation.getOperationId()
                                    : generateOperationId(opEntry.getKey().name(), pathEntry.getKey());

                            apiOperation.setOperationId(operationId);
                            apiOperation.setHttpMethod(opEntry.getKey().name());
                            apiOperation.setPath(pathEntry.getKey());
                            apiOperation.setDescription(operation.getSummary() != null ? operation.getSummary() : operation.getDescription());
                            apiOperation.setSecurity(operation.getSecurity());

                            if (operation.getParameters() != null) {
                                apiOperation.setParameters(operation.getParameters().stream()
                                        .map(this::toApiParameter)
                                        .collect(Collectors.toList()));
                            } else {
                                apiOperation.setParameters(Collections.emptyList());
                            }

                            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                                var jsonContent = operation.getRequestBody().getContent().get("application/json");
                                if (jsonContent != null) {
                                    apiOperation.setRequestBodySchema(jsonContent.getSchema());
                                } else {
                                    operation.getRequestBody().getContent().values().stream()
                                            .findFirst()
                                            .ifPresent(content -> apiOperation.setRequestBodySchema(content.getSchema()));
                                }
                            }

                            return apiOperation;
                        }))
                .collect(Collectors.toMap(ApiOperation::getOperationId, op -> op, (op1, op2) -> op1))); // Handle duplicate operationIds gracefully
        return spec;
    }

    /**
     * A private helper method to convert a Swagger {@link Parameter} object into the
     * internal {@link ApiParameter} model.
     *
     * @param parameter The original parameter object from the parsed OpenAPI specification.
     * @return A simplified {@link ApiParameter} instance.
     */
    private ApiParameter toApiParameter(Parameter parameter) {
        ApiParameter apiParameter = new ApiParameter();
        apiParameter.setName(parameter.getName());
        apiParameter.setIn(parameter.getIn());
        apiParameter.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
        apiParameter.setSchema(parameter.getSchema());
        return apiParameter;
    }

    /**
     * A private helper method to generate a deterministic {@code operationId} when one is
     * not provided in the OpenAPI specification.
     * <p>
     * The generated ID is a concatenation of the lower-cased HTTP method and a sanitized
     * version of the path (e.g., "get_users_by_userId").
     *
     * @param httpMethod The HTTP method (e.g., "GET", "POST").
     * @param path       The API path (e.g., "/users/{userId}").
     * @return A generated, unique operation ID string.
     */
    private String generateOperationId(String httpMethod, String path) {
        String sanitizedPath = path
                .replaceAll("\\{", "by_") // Replace path param openers for clarity
                .replaceAll("[{}/]", "_")   // Replace special characters with underscores
                .replaceAll("__", "_")     // Collapse double underscores
                .replaceAll("^_|_$", "");  // Trim leading/trailing underscores
        return httpMethod.toLowerCase() + "_" + sanitizedPath;
    }
}