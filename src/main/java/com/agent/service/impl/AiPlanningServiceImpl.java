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
import java.util.List;
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

            String jsonPlan = response.getChoices().get(0).getMessage().getContent();
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

    private String buildSystemPrompt(ApiSpecification spec) {
        String availableTools = spec.getOperations().values().stream()
                .map(this::formatOperationAsTool)
                .collect(Collectors.joining("\n"));

        // UPDATED PROMPT:
        return "You are an expert AI agent that translates user requests into a machine-readable JSON execution plan. " +
                "Your task is to create a sequence of steps to fulfill the user's goal using the provided API tools.\n\n" +
                "You must respond with ONLY a valid JSON object that adheres to the ExecutionPlan schema. Do not include any explanations or markdown formatting.\n\n" +
                "## Key Directives:\n" +
                "1. If the user provides all necessary information (like an ID), create a plan with STATIC parameters.\n" +
                "2. If a necessary piece of information is missing from the user's request (e.g., they say 'find a pet by its name' but don't provide a name), you MUST use the `USER_INPUT` source for that parameter. This tells the system to ask the user for that value.\n\n" +
                "## JSON Schema for ExecutionPlan:\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"string\",\n" +
                "      \"reasoning\": \"string\",\n" +
                "      \"parameters\": {\n" +
                "        \"parameter_name\": {\n" +
                // --- ADDED USER_INPUT HERE ---
                "          \"source\": \"STATIC | FROM_STEP | USER_INPUT\",\n" +
                "          \"value\": \"(only for STATIC)\",\n" +
                "          \"stepId\": \"(only for FROM_STEP)\",\n" +
                "          \"jsonPath\": \"(only for FROM_STEP, e.g., $.id)\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "## Available API Tools:\n" +
                availableTools + "\n\n" +
                "--- EXAMPLES ---\n\n" +
                "## Example 1 (Information Provided):\n" +
                "User Query: 'Get the details for pet with ID 987.'\n" +
                "Example JSON Response:\n" +
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
                "## Example 2 (Information Missing):\n" +
                "User Query: 'Find a pet by its status.'\n" +
                "Example JSON Response:\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"findPetsByStatus\",\n" +
                "      \"reasoning\": \"The user wants to find pets by status, but did not specify which status. I need to ask the user for this information.\",\n" +
                "      \"parameters\": {\n" +
                "        \"status\": { \"source\": \"USER_INPUT\" }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "Now, generate the JSON execution plan for the user's request.";
    }

    private String formatOperationAsTool(ApiOperation op) {
        String params = op.getParameters().stream()
                .map(p -> p.getName() + " (" + (p.getSchema() != null ? p.getSchema().toString() : "any") + ")")
                .collect(Collectors.joining(", "));
        return String.format("- operationId: `%s`\n  description: %s\n  parameters: [%s]",
                op.getOperationId(), op.getDescription(), params);
    }
}