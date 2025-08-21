package com.agent.service.impl;

import com.agent.model.ApiSpecification;
import com.agent.model.ExecutionPlan;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

class AiPlanningServiceImplTest {

    public static MockWebServer mockLlmServer;
    private AiPlanningServiceImpl aiPlanningService;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockLlmServer = new MockWebServer();
        mockLlmServer.start();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mockLlmServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        aiPlanningService = new AiPlanningServiceImpl(WebClient.builder());
        String baseUrl = String.format("http://localhost:%s", mockLlmServer.getPort());
        ReflectionTestUtils.setField(aiPlanningService, "llmApiEndpoint", baseUrl);
        ReflectionTestUtils.setField(aiPlanningService, "llmApiKey", "test-key");
        ReflectionTestUtils.setField(aiPlanningService, "llmModel", "test-model");
    }

    @Test
    void createExecutionPlan_shouldBuildCorrectPromptAndParseResponse() throws Exception {
        // --- Arrange ---
        // 1. Create a dummy API spec
        URL resource = getClass().getClassLoader().getResource("test-openapi.json");
        assertThat(resource).isNotNull();
        // FIX: Convert URL to a clean, OS-agnostic file path
        String specPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();
        ApiSpecification spec = new OpenApiServiceImpl().loadAndParseSpec(specPath);

        // 2. Mock the LLM's response
        String llmResponseJson = "{ \"choices\": [ { \"message\": { \"role\": \"assistant\", \"content\": \"{\\\"steps\\\":[{\\\"stepId\\\":\\\"1\\\",\\\"operationId\\\":\\\"getItemById\\\",\\\"reasoning\\\":\\\"Get item\\\",\\\"parameters\\\":{\\\"itemId\\\":{\\\"source\\\":\\\"STATIC\\\",\\\"value\\\":\\\"abc\\\"}}}]}\" } } ] }";
        mockLlmServer.enqueue(new MockResponse()
                .setBody(llmResponseJson)
                .addHeader("Content-Type", "application/json"));

        // --- Act ---
        ExecutionPlan plan = aiPlanningService.createExecutionPlan("get item abc", spec);

        // --- Assert ---
        // Assert the generated plan is correct
        assertThat(plan).isNotNull();
        assertThat(plan.getSteps()).hasSize(1);
        assertThat(plan.getSteps().get(0).getOperationId()).isEqualTo("getItemById");

        // Assert the request sent to the LLM was correct
        RecordedRequest recordedRequest = mockLlmServer.takeRequest();
        String requestBody = recordedRequest.getBody().readUtf8();
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(requestBody).contains("\"role\":\"system\"");
        assertThat(requestBody).contains("operationId: `getItemById`");
        assertThat(requestBody).contains("operationId: `get_items`");
        assertThat(requestBody).contains("\"role\":\"user\",\"content\":\"get item abc\"");
    }
}