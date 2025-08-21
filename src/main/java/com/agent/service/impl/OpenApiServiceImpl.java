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
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.servers.Server;
import org.springframework.stereotype.Service;

@Service
public class OpenApiServiceImpl implements OpenApiService {

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

        // --- NEW: Parse global security schemes ---
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

                            // --- NEW: Parse security requirements for this specific operation ---
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
                .collect(Collectors.toMap(ApiOperation::getOperationId, op -> op, (op1, op2) -> op1)));
        return spec;
    }

    private ApiParameter toApiParameter(Parameter parameter) {
        ApiParameter apiParameter = new ApiParameter();
        apiParameter.setName(parameter.getName());
        apiParameter.setIn(parameter.getIn());
        apiParameter.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
        apiParameter.setSchema(parameter.getSchema());
        return apiParameter;
    }

    private String generateOperationId(String httpMethod, String path) {
        String sanitizedPath = path
                .replaceAll("\\{", "by_")
                .replaceAll("[{}/]", "_")
                .replaceAll("__", "_")
                .replaceAll("^_|_$", "");
        return httpMethod.toLowerCase() + "_" + sanitizedPath;
    }
}