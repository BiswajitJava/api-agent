package com.agent.cli;

import com.agent.cli.ui.Spinner;
import com.agent.exception.ApiAgentException;
import com.agent.model.*;
import com.agent.service.api.AiPlanningService;
import com.agent.service.api.ExecutionEngine;
import com.agent.service.api.OpenApiService;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jline.reader.LineReader;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ShellTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = { "spring.shell.jline.terminal.dumb=true" })
class QueryCommandTest {

    @MockitoBean
    private AiPlanningService aiPlanningService;
    @MockitoBean
    private ExecutionEngine executionEngine;
    @MockitoBean
    private StateService stateService;
    @MockitoBean
    private Spinner spinner;
    @MockitoBean
    private OpenApiService openApiService;

    @Autowired
    private ShellTestClient client;

    private ShellTestClient.InteractiveShellSession session;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public void startShellSession() {
        session = client.interactive().run();
        // The crucial wait for the prompt, which should now work.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(session.screen().toString()).contains("shell:>")
        );
    }

    @AfterAll
    public void stopShellSession() {
        if (session != null) {
            session.write("exit");
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(aiPlanningService, executionEngine, stateService, spinner, openApiService);
        when(spinner.spin(any())).thenAnswer(invocation -> {
            Supplier<?> task = invocation.getArgument(0, Supplier.class);
            return task.get();
        });
    }

    // ... All of your @Test methods remain exactly the same as before ...
    @Test
    void queryCommand_happyPath_succeeds() throws Exception {
        // Arrange
        ApiSpecification fakeSpec = createFakeSpec();
        ExecutionPlan fakePlan = createFakePlan();
        JsonNode fakeResult = objectMapper.readTree("{\"status\":\"success\"}");

        when(stateService.getSpecification("petstore")).thenReturn(fakeSpec);
        when(aiPlanningService.createExecutionPlan(anyString(), any(ApiSpecification.class))).thenReturn(fakePlan);
        when(executionEngine.execute(any(ExecutionPlan.class), eq("petstore"), any(LineReader.class))).thenReturn(fakeResult);

        // Act
        session.write(String.format("query petstore get pet 123%n"));
        session.write(String.format("y%n"));

        // Assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String screen = session.screen().toString();
            assertThat(screen).contains("I have generated the following plan:");
            assertThat(screen).contains("1. Get pet details.");
            assertThat(screen).contains("Execute this plan? [y/N]:");
            assertThat(screen).contains("Executing plan...");
            assertThat(screen).contains("\"status\" : \"success\"");
        });

        verify(executionEngine, times(1)).execute(any(), anyString(), any(LineReader.class));
    }

    @Test
    void queryCommand_whenApiNotLearned_printsError() {
        when(stateService.getSpecification("unknown-api")).thenReturn(null);
        session.write(String.format("query unknown-api do something%n"));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(session.screen().toString()).contains("No API found with alias 'unknown-api'")
        );
    }

    @Test
    void queryCommand_whenUserCancels_abortsExecution() {
        when(stateService.getSpecification("petstore")).thenReturn(createFakeSpec());
        when(aiPlanningService.createExecutionPlan(anyString(), any())).thenReturn(createFakePlan());
        session.write(String.format("query petstore get pet 123%n"));
        session.write(String.format("n%n"));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(session.screen().toString()).contains("Execution cancelled.")
        );
        verify(executionEngine, never()).execute(any(), any(), any());
    }

    @Test
    void queryCommand_verboseFlag_enablesAndDisablesDebugLogging() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level originalLevel = rootLogger.getLevel();
        when(stateService.getSpecification("petstore")).thenReturn(createFakeSpec());
        when(aiPlanningService.createExecutionPlan(anyString(), any())).thenReturn(createFakePlan());
        session.write(String.format("query --verbose petstore get pet 123%n"));
        session.write(String.format("n%n"));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            String screen = session.screen().toString();
            assertThat(screen).contains("-- Verbose mode enabled --");
            assertThat(screen).contains("-- Verbose mode disabled --");
        });
        assertThat(rootLogger.getLevel()).isEqualTo(originalLevel);
    }

    @Test
    void queryCommand_whenExecutionFails_printsGracefulError() {
        when(stateService.getSpecification("petstore")).thenReturn(createFakeSpec());
        when(aiPlanningService.createExecutionPlan(anyString(), any())).thenReturn(createFakePlan());
        when(executionEngine.execute(any(), anyString(), any())).thenThrow(new ApiAgentException("API returned 500 Internal Server Error"));
        session.write(String.format("query petstore get pet 123%n"));
        session.write(String.format("y%n"));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(session.screen().toString()).contains("An error occurred: API returned 500 Internal Server Error")
        );
    }


    // --- Helper Methods ---
    private ApiSpecification createFakeSpec() {
        ApiSpecification spec = new ApiSpecification();
        spec.setOperations(Map.of("getPetById", new ApiOperation()));
        return spec;
    }

    private ExecutionPlan createFakePlan() {
        ExecutionPlan plan = new ExecutionPlan();
        ExecutionStep step = new ExecutionStep();
        step.setStepId("1");
        step.setOperationId("getPetById");
        step.setReasoning("Get pet details.");
        plan.setSteps(List.of(step));
        return plan;
    }
}