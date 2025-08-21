package com.agent.cli;

import com.agent.cli.ui.Spinner;
import com.agent.dto.response.CommandResponse;
import com.agent.model.ExecutionPlan;
import com.agent.service.api.AiPlanningService;
import com.agent.service.api.ExecutionEngine;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jline.reader.LineReader;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class QueryCommand {

    private final StateService stateService;
    private final AiPlanningService aiPlanningService;
    private final ExecutionEngine executionEngine;
    private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final LineReader lineReader;
    private final Spinner spinner;

    public QueryCommand(StateService stateService,
                        AiPlanningService aiPlanningService,
                        ExecutionEngine executionEngine,
                        @Lazy LineReader lineReader,
                        Spinner spinner) {
        this.stateService = stateService;
        this.aiPlanningService = aiPlanningService;
        this.executionEngine = executionEngine;
        this.lineReader = lineReader;
        this.spinner = spinner;
    }

    @ShellMethod(key = "query", value = "Executes a natural language query against a learned API.")
    public void query(
            // --- THE DEFINITIVE FIX ---
            // Make BOTH alias and prompt required named options. This removes all ambiguity.
            @ShellOption(value = {"--alias", "-a"}, help = "The alias of the API to query.") String alias,
            @ShellOption(value = {"--prompt", "-p"}, arity = Integer.MAX_VALUE, help = "The natural language prompt.") String[] prompt,
            @ShellOption(value = {"--verbose", "-v"}, help = "Enable verbose debug logging.", defaultValue = "false", arity = 0) boolean verbose
    ) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level originalLevel = rootLogger.getLevel();
        if (verbose) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            System.out.println("\u001B[35m-- Verbose mode enabled --\u001B[0m");
        }

        try {
            var spec = stateService.getSpecification(alias);
            if (spec == null) {
                System.out.println(new CommandResponse(false, "No API found with alias '" + alias + "'. Use the 'learn' command first.").toAnsiString());
                return;
            }

            String fullPrompt = String.join(" ", prompt);

            ExecutionPlan plan = spinner.spin(() -> aiPlanningService.createExecutionPlan(fullPrompt, spec));

            System.out.println("\nI have generated the following plan:");
            plan.getSteps().forEach(step ->
                    System.out.println("\u001B[33m" + "  " + step.getStepId() + ". " + step.getReasoning() + "\u001B[0m")
            );

            String confirmation = lineReader.readLine("Execute this plan? [y/N]: ");

            if ("y".equalsIgnoreCase(confirmation.trim())) {
                System.out.println("Executing plan...");
                JsonNode finalResult = executionEngine.execute(plan, alias, this.lineReader);
                System.out.println("\n" + formatJson(finalResult));
            } else {
                System.out.println("Execution cancelled.");
            }
        } catch (Exception e) {
            System.out.println(new CommandResponse(false, "An error occurred: " + e.getMessage()).toAnsiString());
        } finally {
            if (verbose) {
                rootLogger.setLevel(originalLevel);
                System.out.println("\u001B[35m-- Verbose mode disabled --\u001B[0m");
            }
        }
    }

    private String formatJson(JsonNode node) {
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to format final JSON result.\"}";
        }
    }
}