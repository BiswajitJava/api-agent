package com.agent.service.impl;

import com.agent.model.*;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jline.reader.LineReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionEngineImplTest {

    public static MockWebServer mockWebServer;
    private ExecutionEngineImpl executionEngine;

    @Mock
    private StateService stateService;

    // --- NEW: Mock the LineReader for testing user input ---
    @Mock
    private LineReader lineReader;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        WebClient testWebClient = WebClient.builder().build();
        executionEngine = new ExecutionEngineImpl(testWebClient, stateService);
    }

    @Test
    void execute_shouldPerformGetRequestWithApiKeyAuth() throws Exception {
        // --- Arrange ---
        // 1. Load Spec
        URL resource = getClass().getClassLoader().getResource("test-openapi.json");
        assertThat(resource).isNotNull();
        String specPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();
        ApiSpecification spec = new OpenApiServiceImpl().loadAndParseSpec(specPath);

        // 2. Override Server URL to point to mock server
        String mockServerHost = String.format("http://localhost:%s", mockWebServer.getPort());
        String serverUrlFromSpec = spec.getServerUrls().get(0);
        spec.setServerUrls(List.of(mockServerHost + serverUrlFromSpec));

        // 3. Mock StateService
        when(stateService.getSpecification("test-api")).thenReturn(spec);
        when(stateService.getCredential("test-api")).thenReturn("my-secret-key");

        // 4. Mock API Response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"123\", \"name\":\"Test Item\"}")
                .addHeader("Content-Type", "application/json"));

        // 5. Create Execution Plan
        ExecutionPlan plan = new ExecutionPlan();
        ExecutionStep step = new ExecutionStep();
        step.setStepId("1");
        step.setOperationId("getItemById");
        ParameterValueSource param = new ParameterValueSource();
        param.setSource(ParameterValueSource.Source.STATIC);
        param.setValue("123");
        step.setParameters(Map.of("itemId", param));
        plan.setSteps(List.of(step));

        // --- Act ---
        // UPDATE: Pass the mocked lineReader to the execute method
        JsonNode result = executionEngine.execute(plan, "test-api", lineReader);

        // --- Assert ---
        assertThat(result).isNotNull();
        assertThat(result.get("id").asText()).isEqualTo("123");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/items/123");
        assertThat(recordedRequest.getHeader("X-API-KEY")).isEqualTo("my-secret-key");
    }

    // --- NEW TEST CASE for Interactive Input ---
    @Test
    void execute_shouldPromptUserForInputWhenSourceIsUserInput() throws Exception {
        // --- Arrange ---
        // 1. Load Spec
        URL resource = getClass().getClassLoader().getResource("test-openapi.json");
        assertThat(resource).isNotNull();
        String specPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();
        ApiSpecification spec = new OpenApiServiceImpl().loadAndParseSpec(specPath);

        // 2. Override Server URL
        String mockServerHost = String.format("http://localhost:%s", mockWebServer.getPort());
        String serverUrlFromSpec = spec.getServerUrls().get(0);
        spec.setServerUrls(List.of(mockServerHost + serverUrlFromSpec));

        // 3. Mock StateService (no credentials needed for this test)
        when(stateService.getSpecification("test-api")).thenReturn(spec);

        // 4. Mock the user's console input
        when(lineReader.readLine(anyString())).thenReturn("active");

        // 5. Mock the API response
        mockWebServer.enqueue(new MockResponse()
                .setBody("[{\"id\":\"item1\"}]")
                .addHeader("Content-Type", "application/json"));

        // 6. Create a plan that requires USER_INPUT
        ExecutionPlan plan = new ExecutionPlan();
        ExecutionStep step = new ExecutionStep();
        step.setStepId("1");
        step.setOperationId("get_items"); // The operation that takes a 'status' query param
        ParameterValueSource param = new ParameterValueSource();
        param.setSource(ParameterValueSource.Source.USER_INPUT); // This is the key part
        step.setParameters(Map.of("status", param));
        plan.setSteps(List.of(step));

        // --- Act ---
        JsonNode result = executionEngine.execute(plan, "test-api", lineReader);

        // --- Assert ---
        assertThat(result).isNotNull();
        assertThat(result.get(0).get("id").asText()).isEqualTo("item1");

        // Verify the request was built using the user's input
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/items?status=active");
    }
}