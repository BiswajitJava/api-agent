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

/**
 * A Spring Shell component for executing natural language queries against a learned API.
 * This command orchestrates the process of creating an execution plan from a user's prompt,
 * asking for user confirmation, and then executing the plan to interact with the API.
 */
@ShellComponent
public class QueryCommand {

    private final StateService stateService;
    private final AiPlanningService aiPlanningService;
    private final ExecutionEngine executionEngine;
    private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final LineReader lineReader;
    private final Spinner spinner;

    /**
     * Constructs a new QueryCommand with the required services and UI components.
     *
     * @param stateService      Service to retrieve persisted API specifications.
     * @param aiPlanningService Service to generate an execution plan from a natural language prompt.
     * @param executionEngine   Service to execute the generated plan.
     * @param lineReader        JLine component to read user input from the console. [3, 4]
     * @param spinner           UI component to display a spinner during long-running operations.
     */
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

    /**
     * Executes a natural language query against a specified, previously learned API.
     * <p>
     * This method first retrieves the API specification using the provided alias. It then uses
     * the AI planning service to generate a multi-step execution plan based on the user's prompt.
     * The plan is displayed to the user for confirmation. If the user approves, the execution
     * engine runs the plan, and the final result is printed to the console as a formatted JSON.
     *
     * @param alias   The alias of the API to query, specified with --alias or -a.
     * @param prompt  The natural language prompt, specified with --prompt or -p. Can be multiple words.
     * @param verbose If true, enables detailed debug logging for the duration of the command execution.
     */
    @ShellMethod(key = "query", value = "Executes a natural language query against a learned API.")
    public void query(
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

    /**
     * A private helper method to format a Jackson JsonNode into an indented (pretty-printed) JSON string.
     *
     * @param node The JsonNode to format.
     * @return A formatted JSON string, or an error message if formatting fails.
     */
    private String formatJson(JsonNode node) {
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to format final JSON result.\"}";
        }
    }
}