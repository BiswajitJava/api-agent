package com.agent.service.impl;

import com.agent.exception.ApiAgentException;
import com.agent.model.*;
import com.agent.service.api.ExecutionEngine;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class ExecutionEngineImpl implements ExecutionEngine {

    private final WebClient webClient;
    private final StateService stateService;

    public ExecutionEngineImpl(WebClient webClient, StateService stateService) {
        this.webClient = webClient;
        this.stateService = stateService;
    }

    @Override
    public JsonNode execute(ExecutionPlan plan, String alias, LineReader lineReader) {
        ApiSpecification spec = stateService.getSpecification(alias);
        if (spec == null) {
            throw new ApiAgentException("No API specification found for alias: " + alias);
        }

        Map<String, JsonNode> stepResults = new HashMap<>();
        JsonNode lastResult = null;

        for (ExecutionStep step : plan.getSteps()) {
            ApiOperation operation = spec.getOperations().get(step.getOperationId());
            if (operation == null) {
                throw new ApiAgentException("Invalid plan: Operation ID '" + step.getOperationId() + "' not found in API spec.");
            }

            log.info("Executing Step {}: {}", step.getStepId(), operation.getOperationId());

            try {
                Map<String, Object> resolvedParameters = resolveParameters(step, stepResults, lineReader);
                WebClient.RequestHeadersSpec<?> requestSpec = buildRequest(operation, resolvedParameters, alias, spec);
                lastResult = requestSpec.retrieve().bodyToMono(JsonNode.class).block();
                stepResults.put(step.getStepId(), lastResult);
                log.info("Step {} successful.", step.getStepId());

            } catch (WebClientResponseException e) {
                log.error("API call for step {} failed with status {} and body: {}", step.getStepId(), e.getStatusCode(), e.getResponseBodyAsString());
                throw new ApiAgentException("Step " + step.getStepId() + " (" + operation.getOperationId() + ") failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
            } catch (Exception e) {
                log.error("An unexpected error occurred during step {}", step.getStepId(), e);
                throw new ApiAgentException("Execution failed at step " + step.getStepId() + ": " + e.getMessage(), e);
            }
        }
        return lastResult;
    }

    private Map<String, Object> resolveParameters(ExecutionStep step, Map<String, JsonNode> stepResults, LineReader lineReader) {
        Map<String, Object> resolved = new HashMap<>();
        if (step.getParameters() == null) {
            return resolved;
        }

        for (Map.Entry<String, ParameterValueSource> entry : step.getParameters().entrySet()) {
            String paramName = entry.getKey();
            ParameterValueSource source = entry.getValue();
            Object value;

            switch (source.getSource()) {
                case USER_INPUT:
                    log.debug("  Resolving param '{}' from USER_INPUT", paramName);
                    String prompt = "\u001B[36m" + "Please provide a value for '" + paramName + "': " + "\u001B[0m";
                    String input = lineReader.readLine(prompt);
                    value = input.trim();
                    break;
                case STATIC:
                    value = source.getValue();
                    log.debug("  Resolving param '{}' from STATIC value: {}", paramName, value);
                    break;
                case FROM_STEP:
                    JsonNode previousResult = stepResults.get(source.getStepId());
                    if (previousResult == null) {
                        throw new ApiAgentException("Invalid plan: Step " + step.getStepId() + " depends on a non-existent or failed step " + source.getStepId());
                    }
                    try {
                        value = JsonPath.read(previousResult.toString(), source.getJsonPath());
                        log.debug("  Resolving param '{}' from Step {} using JsonPath '{}'. Value: {}", paramName, source.getStepId(), source.getJsonPath(), value);
                    } catch (Exception e) {
                        throw new ApiAgentException("Failed to resolve parameter '" + paramName + "' using JsonPath '" + source.getJsonPath() + "'", e);
                    }
                    break;
                default:
                    throw new ApiAgentException("Unknown parameter source: " + source.getSource());
            }
            resolved.put(paramName, value);
        }
        return resolved;
    }

    private WebClient.RequestHeadersSpec<?> buildRequest(ApiOperation operation, Map<String, Object> params, String alias, ApiSpecification spec) {
        if (spec.getServerUrls() == null || spec.getServerUrls().isEmpty()) {
            throw new ApiAgentException("Cannot execute request: No server URL found in the API specification.");
        }
        String baseUri = spec.getServerUrls().getFirst();
        String finalPath = operation.getPath();

        // Substitute path parameters
        if (operation.getParameters() != null) {
            for (ApiParameter apiParam : operation.getParameters()) {
                if ("path".equals(apiParam.getIn()) && params.containsKey(apiParam.getName())) {
                    String placeholder = "{" + apiParam.getName() + "}";
                    finalPath = finalPath.replace(placeholder, String.valueOf(params.get(apiParam.getName())));
                }
            }
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(finalPath);
        // Add query parameters
        if (operation.getParameters() != null) {
            operation.getParameters().stream()
                    .filter(p -> "query".equals(p.getIn()) && params.containsKey(p.getName()))
                    .forEach(p -> uriBuilder.queryParam(p.getName(), params.get(p.getName())));
        }

        String fullUri = baseUri + uriBuilder.build().toUriString();
        log.debug("Building {} request for full URI: {}", operation.getHttpMethod().toUpperCase(), fullUri);

        WebClient.RequestBodySpec requestSpec;
        String httpMethod = operation.getHttpMethod().toUpperCase();

        requestSpec = switch (httpMethod) {
            case "GET" -> (WebClient.RequestBodySpec) webClient.get().uri(fullUri);
            case "POST" -> webClient.post().uri(fullUri);
            case "PUT" -> webClient.put().uri(fullUri);
            case "DELETE" -> (WebClient.RequestBodySpec) webClient.delete().uri(fullUri);
            default -> throw new ApiAgentException("Unsupported HTTP method: " + httpMethod);
        };

        // --- BODY ASSEMBLY LOGIC (SAFE CASTING) ---
        if (params.containsKey("__requestBody__")) {
            // Case 1: The AI provided a complete, static request body.
            requestSpec.bodyValue(params.get("__requestBody__"));
        } else if (operation.getRequestBodySchema() instanceof Map<?, ?> requestBodySchemaMap) {
            // Case 2: Assemble the body from individual params, with safe casting.
            Map<String, Object> requestBody = new HashMap<>();

            // Safely cast the schema to a Map.
            Object rawProperties = requestBodySchemaMap.get("properties");

            if (rawProperties instanceof Map<?, ?> schemaProperties) {
                // Safely cast the properties object to a Map.

                for (Object key : schemaProperties.keySet()) {
                    String paramName = (String) key;
                    if (params.containsKey(paramName)) {
                        Object rawParamSchema = schemaProperties.get(paramName);

                        // Safely check and handle array types for comma-separated input.
                        if (rawParamSchema instanceof Map<?, ?> paramSchema) {
                            if ("array".equals(paramSchema.get("type")) && params.get(paramName) instanceof String) {
                                String[] items = ((String) params.get(paramName)).split(",");
                                requestBody.put(paramName, items);
                                continue; // Skip to next parameter
                            }
                        }
                        // For all other types, just put the value directly.
                        requestBody.put(paramName, params.get(paramName));
                    }
                }
            }

            if (!requestBody.isEmpty()) {
                log.debug("Attaching assembled request body: {}", requestBody);
                requestSpec.bodyValue(requestBody);
            }
        }
        // --- END OF BODY ASSEMBLY LOGIC ---

        // Apply authentication
        String credential = stateService.getCredential(alias);
        if (credential != null && operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            operation.getSecurity().getFirst().forEach((name, scopes) -> {
                SecurityScheme scheme = spec.getSecuritySchemes().get(name);
                if (scheme != null) {
                    log.debug("Applying security scheme '{}' of type {}", name, scheme.getType());
                    if (scheme.getType() == SecurityScheme.Type.APIKEY && scheme.getIn() == SecurityScheme.In.HEADER) {
                        requestSpec.header(scheme.getName(), credential);
                    }
                }
            });
        }
        return requestSpec;
    }
}