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

/**
 * An implementation of the {@link ExecutionEngine} that processes an {@link ExecutionPlan}
 * step-by-step to interact with an API.
 * <p>
 * This engine is responsible for resolving parameters for each step, building and sending
 * HTTP requests, handling authentication, and passing results between steps.
 */
@Service
@Slf4j
public class ExecutionEngineImpl implements ExecutionEngine {

    private final WebClient webClient;
    private final StateService stateService;

    /**
     * Constructs the execution engine with necessary dependencies.
     *
     * @param webClient    The reactive web client for making HTTP requests.
     * @param stateService The service to retrieve API specifications and credentials.
     */
    public ExecutionEngineImpl(WebClient webClient, StateService stateService) {
        this.webClient = webClient;
        this.stateService = stateService;
    }

    /**
     * Executes a given plan against the API specified by the alias.
     * <p>
     * The method iterates through each {@link ExecutionStep} in the plan, resolves its parameters,
     * builds the appropriate HTTP request, executes it, and stores the result. If a step
     * depends on the output of a previous step, that output is made available.
     *
     * @param plan       The {@link ExecutionPlan} to execute.
     * @param alias      The alias of the API to target.
     * @param lineReader The JLine reader to prompt the user for input if required by a step.
     * @return The {@link JsonNode} result from the final step of the plan.
     * @throws ApiAgentException if the API spec is not found, the plan is invalid,
     *                           an API call fails, or parameter resolution fails.
     */
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

    /**
     * Resolves the values for all parameters required by an {@link ExecutionStep}.
     * <p>
     * It determines the value for each parameter based on its defined {@link ParameterValueSource}.
     * It can use a static value, prompt the user for input, or extract a value from the
     * result of a previous step using a JSONPath expression.
     *
     * @param step        The current execution step.
     * @param stepResults A map containing the JSON results of previously executed steps.
     * @param lineReader  The reader for prompting the user for input.
     * @return A map of parameter names to their resolved object values.
     * @throws ApiAgentException if a source is unknown, a dependent step has failed, or JSONPath extraction fails.
     */
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

    /**
     * Constructs a {@link WebClient.RequestHeadersSpec} for a given API operation.
     * <p>
     * This method assembles the full request URI by combining the server URL with the operation's path,
     * substituting path parameters and adding query parameters. It also intelligently applies
     * security credentials (e.g., API keys, Bearer tokens) based on the operation's security requirements.
     *
     * @param operation The {@link ApiOperation} to build a request for.
     * @param params    A map of resolved parameter names and their values.
     * @param alias     The alias of the API, used to retrieve credentials.
     * @param spec      The full {@link ApiSpecification} for context.
     * @return A configurable {@link WebClient.RequestHeadersSpec} ready to be executed.
     * @throws ApiAgentException if no server URL is found or the HTTP method is unsupported.
     */
    private WebClient.RequestHeadersSpec<?> buildRequest(ApiOperation operation, Map<String, Object> params, String alias, ApiSpecification spec) {
        if (spec.getServerUrls() == null || spec.getServerUrls().isEmpty()) {
            throw new ApiAgentException("Cannot execute request: No server URL found in the API specification.");
        }
        String baseUri = spec.getServerUrls().getFirst();

        String finalPath = operation.getPath();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (finalPath.contains(placeholder)) {
                finalPath = finalPath.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(finalPath);
        operation.getParameters().stream()
                .filter(p -> "query".equals(p.getIn()) && params.containsKey(p.getName()))
                .forEach(p -> uriBuilder.queryParam(p.getName(), params.get(p.getName())));

        String fullUri = baseUri + uriBuilder.build().toUriString();
        log.debug("Building {} request for full URI: {}", operation.getHttpMethod().toUpperCase(), fullUri);

        WebClient.RequestHeadersSpec<?> requestSpec;
        String httpMethod = operation.getHttpMethod().toUpperCase();

        requestSpec = switch (httpMethod) {
            case "GET" -> webClient.get().uri(fullUri);
            case "POST" -> webClient.post().uri(fullUri);
            // Add cases for PUT, DELETE, etc. as needed
            default -> throw new ApiAgentException("Unsupported HTTP method: " + httpMethod);
        };

        // Apply intelligent, spec-aware authentication
        String credential = stateService.getCredential(alias);
        if (credential != null && operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            operation.getSecurity().getFirst().forEach((name, scopes) -> {
                SecurityScheme scheme = spec.getSecuritySchemes().get(name);
                if (scheme != null) {
                    log.debug("Applying security scheme '{}' of type {}", name, scheme.getType());
                    switch (scheme.getType()) {
                        case APIKEY:
                            if (scheme.getIn() == SecurityScheme.In.HEADER) {
                                requestSpec.header(scheme.getName(), credential);
                            }
                            break;
                        case HTTP:
                            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                                requestSpec.header("Authorization", "Bearer " + credential);
                            }
                            break;
                    }
                }
            });
        }

        return requestSpec;
    }
}