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

/**
 * An implementation of the {@link AiPlanningService} that uses a Large Language Model (LLM)
 * to translate a user's natural language prompt into a structured {@link ExecutionPlan}.
 * <p>
 * This service is the core "brain" of the API Agent. It is responsible for:
 * <ul>
 *   <li>Dynamically constructing a detailed system prompt based on the learned API specification.</li>
 *   <li>Formatting the API operations as "tools" that the LLM can understand and use.</li>
 *   <li>Modifying the prompt's instructions based on the user's intent (e.g., enabling AI-powered autofill).</li>
 *   <li>Sending the request to the configured LLM API endpoint.</li>
 *   <li>Parsing and validating the JSON response from the LLM to ensure it is a safe and valid plan.</li>
 * </ul>
 */
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

    /**
     * Constructs the AI Planning Service.
     *
     * @param webClientBuilder The Spring-configured WebClient builder used to create a client
     *                         for communicating with the LLM API.
     */
    public AiPlanningServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation builds a comprehensive system prompt, sends it along with the user's
     * prompt to the configured LLM, and parses the resulting JSON into an {@link ExecutionPlan}.
     * The behavior of the generated plan is heavily influenced by the {@code autofill} flag.
     *
     * @param prompt   The user's natural language goal (e.g., "add a new pet named Fido").
     * @param spec     The {@link ApiSpecification} containing the tools (operations) the AI can use.
     * @param autofill If true, the system prompt will instruct the AI to invent and provide a complete
     *                 request body for operations that require one. If false, the AI is instructed
     *                 to create a plan that asks the user for each required field interactively.
     * @return A validated {@link ExecutionPlan} ready for execution.
     * @throws ApiAgentException if the LLM response is empty, invalid, or cannot be parsed,
     *                           or if the generated plan fails validation.
     */
    @Override
    public ExecutionPlan createExecutionPlan(String prompt, ApiSpecification spec, boolean autofill) {
        String systemPrompt = buildSystemPrompt(spec, autofill);
        LlmRequest llmRequest = new LlmRequest(
                llmModel,
                Arrays.asList(
                        new LlmMessage("system", systemPrompt),
                        new LlmMessage("user", prompt)
                )
        );

        log.info("Sending request to LLM with autofill set to: {}", autofill);
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

    /**
     * Validates an AI-generated {@link ExecutionPlan} to ensure its safety and correctness.
     * It checks that the plan is not empty and that every step refers to a valid {@code operationId}
     * that exists in the provided {@link ApiSpecification}.
     *
     * @param plan The {@link ExecutionPlan} generated by the AI.
     * @param spec The known {@link ApiSpecification} to validate against.
     * @throws ApiAgentException if the plan is null, empty, or contains invalid operation IDs.
     */
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

    /**
     * Formats a single {@link ApiOperation} into a structured string that the LLM can interpret as a usable "tool".
     * This includes the operation's ID, description, parameters, and request body schema.
     *
     * @param op The {@link ApiOperation} to format.
     * @return A string representing the API operation, suitable for inclusion in the system prompt.
     */
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
        toolSpec.append(String.format("- operationId: `%s`\n  description: %s\n  parameters: [%s]", op.getOperationId(), op.getDescription(), params));
        if (op.getRequestBodySchema() != null) {
            try {
                String requestBodyJson = simpleMapper.writerWithDefaultPrettyPrinter().writeValueAsString(op.getRequestBodySchema());
                toolSpec.append("\n  requestBodySchema: \n").append(requestBodyJson);
            } catch (JsonProcessingException e) { /* ignore */ }
        }
        return toolSpec.toString();
    }

    /**
     * Constructs the complete system prompt that will be sent to the LLM.
     * <p>
     * This is the core of the prompt engineering. It assembles several critical pieces of information:
     * <ul>
     *   <li>A role definition for the AI ("You are an expert AI agent...").</li>
     *   <li>The required JSON output schema for the {@link ExecutionPlan}.</li>
     *   <li>Key directives on how to handle different scenarios (e.g., user-provided data vs. missing data).</li>
     *   <li>A dynamic instruction for AI-powered autofill, which is included only if {@code autofill} is true.</li>
     *   <li>A list of all available API "tools," formatted by {@link #formatOperationAsTool}.</li>
     *   <li>A set of few-shot examples demonstrating the correct JSON output for various user queries.</li>
     * </ul>
     *
     * @param spec     The {@link ApiSpecification} used to generate the list of available tools.
     * @param autofill A boolean flag that determines whether to include the AI-powered autofill instruction.
     * @return A fully-formed string containing the system prompt.
     */
    private String buildSystemPrompt(ApiSpecification spec, boolean autofill) {
        String availableTools = spec.getOperations().values().stream()
                .map(this::formatOperationAsTool)
                .collect(Collectors.joining("\n\n"));

        String autofillInstruction = "";
        if (autofill) {
            autofillInstruction = "4. **AI-POWERED AUTOFILL**: The user has enabled autofill. For any POST/PUT operation where the request body is needed but not provided by the user, you MUST invent a plausible, complete, and schema-compliant JSON object for the request body. This generated object must be placed inside a `STATIC` source parameter named `__requestBody__`. Do NOT use `USER_INPUT` for individual fields when in this mode.\n";
        }

        return "You are an expert AI agent that translates user requests into a machine-readable JSON execution plan. " +
                "Your task is to create a sequence of steps to fulfill the user's goal using the provided API tools.\n\n" +
                "You must respond with ONLY a valid JSON object that adheres to the ExecutionPlan schema. Do not include any explanations or markdown formatting.\n\n" +
                "## Key Directives:\n" +
                "1. **STATIC**: If the user provides the information directly (e.g., 'get pet with ID 123'), use the `STATIC` source.\n" +
                "2. **STATIC BODY**: For POST/PUT requests where the user provides all data, create a single parameter named `__requestBody__` with a `STATIC` source containing the full JSON object.\n" +
                "3. **INTERACTIVE BODY**: If autofill is OFF and the user does NOT provide data for a POST/PUT (e.g., 'add a new pet'), you MUST create a separate `USER_INPUT` parameter for EACH field required by the `requestBodySchema`.\n" +
                autofillInstruction +
                "\n## JSON Schema for ExecutionPlan:\n" +
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
                "## Example 3 (POST Request with MISSING Data - INTERACTIVE / autofill=false):\n" +
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
                "## Example 4 (POST Request with MISSING Data - AI AUTOFILL / autofill=true):\n" +
                "User Query: 'Add a new dog to the system.'\n" +
                "```json\n" +
                "{\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"stepId\": \"1\",\n" +
                "      \"operationId\": \"addPet\",\n" +
                "      \"reasoning\": \"The user wants to add a new pet using autofill, so I will invent a plausible and complete request body based on the API schema.\",\n" +
                "      \"parameters\": {\n" +
                "        \"__requestBody__\": {\n" +
                "          \"source\": \"STATIC\",\n" +
                "          \"value\": {\n" +
                "            \"id\": 481516,\n" +
                "            \"name\": \"Buddy\",\n" +
                "            \"status\": \"available\",\n" +
                "            \"photoUrls\": [\"https://example.com/photos/buddy_the_dog.jpg\"],\n" +
                "            \"tags\": [ { \"id\": 1, \"name\": \"friendly\" }, { \"id\": 2, \"name\": \"energetic\" } ]\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "Now, generate the JSON execution plan for the user's request.";
    }
}