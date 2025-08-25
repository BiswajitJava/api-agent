package com.agent.service.impl;

import com.agent.dto.llm.LlmMessage;
import com.agent.dto.llm.LlmRequest;
import com.agent.dto.llm.LlmResponse;
import com.agent.exception.ApiAgentException;
import com.agent.model.ApiOperation;
import com.agent.model.ApiSpecification;
import com.agent.model.ExecutionPlan;
import com.agent.service.api.AiPlanningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AiPlanningServiceImpl implements AiPlanningService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.api.key}")
    private String llmApiKey;

    @Value("${llm.api.endpoint}")
    private String llmApiEndpoint;

    @Value("${llm.model}")
    private String llmModel;

    public AiPlanningServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public ExecutionPlan createExecutionPlan(String prompt, ApiSpecification spec) {
        String systemPrompt = buildSystemPrompt(spec);
        LlmRequest llmRequest = new LlmRequest(
                llmModel,
                Arrays.asList(
                        new LlmMessage("system", systemPrompt),
                        new LlmMessage("user", prompt)
                )
        );

        log.info("Sending request to LLM...");
        try {
            LlmResponse response = webClient.post()
                    .uri(llmApiEndpoint)
                    .header("Authorization", "Bearer " + llmApiKey)
                    .body(Mono.just(llmRequest), LlmRequest.class)
                    .retrieve()
                    .bodyToMono(LlmResponse.class)
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new ApiAgentException("Received an empty or invalid response from the LLM.");
            }

            String jsonPlan = response.getChoices().getFirst().getMessage().getContent();
            log.debug("LLM JSON Response: {}", jsonPlan);

            ExecutionPlan plan = objectMapper.readValue(jsonPlan, ExecutionPlan.class);
            validatePlan(plan, spec);
            return plan;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON response from LLM", e);
            throw new ApiAgentException("Failed to parse the execution plan from the AI. The response was not valid JSON.", e);
        } catch (Exception e) {
            log.error("Error calling LLM API", e);
            throw new ApiAgentException("An error occurred while communicating with the AI.", e);
        }
    }

    private void validatePlan(ExecutionPlan plan, ApiSpecification spec) {
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new ApiAgentException("AI generated an empty or invalid plan.");
        }
        for (var step : plan.getSteps()) {
            if (!spec.getOperations().containsKey(step.getOperationId())) {
                throw new ApiAgentException("AI generated a plan with an invalid operationId: " + step.getOperationId());
            }
        }
        log.info("Successfully validated the AI-generated execution plan.");
    }

    private String formatOperationAsTool(ApiOperation op) {
        ObjectMapper simpleMapper = new ObjectMapper();

        String params = op.getParameters() != null ? op.getParameters().stream()
                .map(p -> {
                    try {
                        return p.getName() + " (" + simpleMapper.writeValueAsString(p.getSchema()) + ")";
                    } catch (JsonProcessingException e) {
                        return p.getName() + " (schema unavailable)";
                    }
                })
                .collect(Collectors.joining(", ")) : "";

        StringBuilder toolSpec = new StringBuilder();
        toolSpec.append(String.format("- operationId: `%s`\n  description: %s\n  parameters: [%s]",
                op.getOperationId(), op.getDescription(), params));

        if (op.getRequestBodySchema() != null) {
            try {
                String requestBodyJson = simpleMapper.writerWithDefaultPrettyPrinter().writeValueAsString(op.getRequestBodySchema());
                toolSpec.append("\n  requestBodySchema: \n").append(requestBodyJson);
            } catch (JsonProcessingException e) {
                // ignore
            }
        }
        return toolSpec.toString();
    }

    private String buildSystemPrompt(ApiSpecification spec) {
        String availableTools = spec.getOperations().values().stream()
                .map(this::formatOperationAsTool)
                .collect(Collectors.joining("\n\n"));

        return "You are an expert AI agent that translates user requests into a machine-readable JSON execution plan. " +
                "Your task is to create a sequence of steps to fulfill the user's goal using the provided API tools.\n\n" +
                "You must respond with ONLY a valid JSON object that adheres to the ExecutionPlan schema. Do not include any explanations or markdown formatting.\n\n" +
                "## Key Directives:\n" +
                "1. **STATIC**: If the user provides the information directly (e.g., 'get pet with ID 123'), use the `STATIC` source.\n" +
                "2. **STATIC BODY**: For POST/PUT requests where the user provides all data, create a single parameter named `__requestBody__` with a `STATIC` source containing the full JSON object.\n" +
                "3. **INTERACTIVE BODY**: For POST/PUT requests where the user does NOT provide the data (e.g., 'add a new pet'), you MUST create a separate `USER_INPUT` parameter for EACH field required by the `requestBodySchema`. Do NOT ask for `__requestBody__` directly.\n\n" +
                "## JSON Schema for ExecutionPlan:\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"string\",\n" +
                "      \"reasoning\": \"string\",\n" +
                "      \"parameters\": {\n" +
                "        \"parameter_name_or___requestBody__\": {\n" +
                "          \"source\": \"STATIC | FROM_STEP | USER_INPUT\",\n" +
                "          \"value\": \"(for STATIC source, this can be a string, a number, or a full JSON object for __requestBody__)\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "## Available API Tools:\n" +
                availableTools + "\n\n" +
                "--- EXAMPLES ---\n\n" +
                "## Example 1 (GET Request):\n" +
                "User Query: 'Get the details for pet with ID 987.'\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"getPetById\",\n" +
                "      \"reasoning\": \"The user provided the pet ID, so I can directly fetch its details.\",\n" +
                "      \"parameters\": {\n" +
                "        \"petId\": { \"source\": \"STATIC\", \"value\": \"987\" }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "## Example 2 (POST Request with User-Provided Data):\n" +
                "User Query: 'Add a new pet named Fido with ID 12345. It is available.'\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"addPet\",\n" +
                "      \"reasoning\": \"The user wants to add a new pet and has provided all the necessary information to construct the request body.\",\n" +
                "      \"parameters\": {\n" +
                "        \"__requestBody__\": {\n" +
                "          \"source\": \"STATIC\",\n" +
                "          \"value\": {\n" +
                "            \"id\": 12345,\n" +
                "            \"name\": \"Fido\",\n" +
                "            \"status\": \"available\",\n" +
                "            \"photoUrls\": []\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "## Example 3 (POST Request with MISSING Data - INTERACTIVE):\n" +
                "User Query: 'Add a new pet to the store.'\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"addPet\",\n" +
                "      \"reasoning\": \"The user wants to add a new pet but has not provided the data. I will ask for each required field to build the request body.\",\n" +
                "      \"parameters\": {\n" +
                "        \"name\": { \"source\": \"USER_INPUT\" },\n" +
                "        \"photoUrls\": { \"source\": \"USER_INPUT\" },\n" +
                "        \"id\": { \"source\": \"USER_INPUT\" },\n" +
                "        \"status\": { \"source\": \"USER_INPUT\" }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "Now, generate the JSON execution plan for the user's request.";
    }
}