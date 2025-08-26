package com.agent.cli;

import com.agent.cli.ui.Spinner;
import com.agent.dto.response.CommandResponse;
import com.agent.model.ExecutionPlan;
import com.agent.service.api.AiPlanningService;
import com.agent.service.api.ExecutionEngine;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jline.reader.LineReader;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Iterator;
import java.util.Map;

/**
 * A Spring Shell component for executing natural language queries against a learned API.
 * This command orchestrates the entire process:
 * 1.  It sends the user's prompt to an AI service to generate a multi-step execution plan.
 * 2.  It displays the plan to the user for confirmation.
 * 3.  Upon approval, it executes the plan, interacting with the live API.
 * It includes a powerful `--autofill` mode that instructs the AI to generate plausible,
 * context-aware data for request bodies, streamlining testing and development workflows.
 */
@ShellComponent
public class QueryCommand {

    // --- ANSI Color Constants for pretty-printing JSON ---
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

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
     * @param lineReader        JLine component to read user input from the console.
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
     * This method orchestrates the primary workflow of the agent. It retrieves the API
     * specification for the given alias and passes the user's prompt to the {@link AiPlanningService}.
     * The AI service then generates a multi-step {@link ExecutionPlan}.
     * <p>
     * The plan is displayed to the user for confirmation. If the user approves, the {@link ExecutionEngine}
     * runs the plan, making the actual API calls. The final result is then pretty-printed in color to the console.
     * The `--autofill` flag provides a powerful alternative to the default interactive mode.
     *
     * @param alias    The alias of the API to query, specified with `--alias` or `-a`.
     * @param prompt   The natural language prompt, specified with `--prompt` or `-p`.
     * @param verbose  If true, enables detailed debug logging for the duration of the command execution.
     * @param autofill If true, instructs the AI Planning Service to generate plausible, complete request bodies
     *                 for any POST/PUT operations where the user has not provided the data in the prompt. This
     *                 bypasses the need for interactive user input for body fields. If false (the default),
     *                 the agent will enter an interactive mode and prompt the user for each required field.
     */
    @ShellMethod(key = "query", value = "Executes a natural language query against a learned API.")
    public void query(
            @ShellOption(value = {"--alias", "-a"}, help = "The alias of the API to query.") String alias,
            @ShellOption(value = {"--prompt", "-p"}, arity = Integer.MAX_VALUE, help = "The natural language prompt.") String[] prompt,
            @ShellOption(value = {"--verbose", "-v"}, help = "Enable verbose debug logging.", defaultValue = "false", arity = 0) boolean verbose,
            @ShellOption(value = {"--autofill", "-af"}, help = "Automatically fill request bodies with AI-generated data.", defaultValue = "false", arity = 0) boolean autofill
    ) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level originalLevel = rootLogger.getLevel();
        if (verbose) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            System.out.println(ANSI_PURPLE + "-- Verbose mode enabled --" + ANSI_RESET);
        }

        try {
            var spec = stateService.getSpecification(alias);
            if (spec == null) {
                System.out.println(new CommandResponse(false, "No API found with alias '" + alias + "'. Use the 'learn' command first.").toAnsiString());
                return;
            }

            String fullPrompt = String.join(" ", prompt);

            ExecutionPlan plan = spinner.spin(() -> aiPlanningService.createExecutionPlan(fullPrompt, spec, autofill));

            System.out.println("\nI have generated the following plan:");
            plan.getSteps().forEach(step ->
                    System.out.println(ANSI_YELLOW + "  " + step.getStepId() + ". " + step.getReasoning() + ANSI_RESET)
            );

            if (autofill) {
                System.out.println(ANSI_PURPLE + "\n-- AI Autofill is active. Request bodies have been generated by the AI. --" + ANSI_RESET);
            }

            String confirmation = lineReader.readLine(ANSI_CYAN + "Execute this plan? [y/N]: " + ANSI_RESET);

            if ("y".equalsIgnoreCase(confirmation.trim())) {
                System.out.println("Executing plan...");
                JsonNode finalResult = executionEngine.execute(plan, alias, this.lineReader, autofill);
                System.out.println("\n" + formatJsonWithColor(finalResult));
            } else {
                System.out.println("Execution cancelled.");
            }
        } catch (Exception e) {
            System.out.println(new CommandResponse(false, "An error occurred: " + e.getMessage()).toAnsiString());
        } finally {
            if (verbose) {
                rootLogger.setLevel(originalLevel);
                System.out.println(ANSI_PURPLE + "-- Verbose mode disabled --" + ANSI_RESET);
            }
        }
    }

    /**
     * A helper method to format a Jackson JsonNode into a pretty-printed, colorized JSON string for console output.
     *
     * @param node The JsonNode to format.
     * @return A formatted and colorized JSON string, or a colored "null" string if the node is null.
     */
    private String formatJsonWithColor(JsonNode node) {
        if (node == null) {
            return ANSI_PURPLE + "null" + ANSI_RESET;
        }
        StringBuilder sb = new StringBuilder();
        buildColoredJsonString(node, sb, 0);
        return sb.toString();
    }

    /**
     * A recursive helper that traverses a JsonNode to build a colorized JSON string with proper indentation.
     * It applies different ANSI colors based on the JSON element type (key, string, number, boolean, null).
     *
     * @param node        The current JsonNode to process.
     * @param sb          The StringBuilder to which the colored string is appended.
     * @param indentLevel The current indentation level, used to generate leading spaces.
     */
    private void buildColoredJsonString(JsonNode node, StringBuilder sb, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        if (node.isObject()) {
            sb.append(ANSI_WHITE).append("{").append(ANSI_RESET).append("\n");
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sb.append(indent).append("  ").append(ANSI_CYAN).append("\"").append(field.getKey()).append("\"").append(ANSI_RESET).append(": ");
                buildColoredJsonString(field.getValue(), sb, indentLevel + 1);
                if (fields.hasNext()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent).append(ANSI_WHITE).append("}").append(ANSI_RESET);
        } else if (node.isArray()) {
            sb.append(ANSI_WHITE).append("[").append(ANSI_RESET).append("\n");
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode element = elements.next();
                sb.append(indent).append("  ");
                buildColoredJsonString(element, sb, indentLevel + 1);
                if (elements.hasNext()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent).append(ANSI_WHITE).append("]").append(ANSI_RESET);
        } else if (node.isTextual()) {
            sb.append(ANSI_GREEN).append("\"").append(node.asText()).append("\"").append(ANSI_RESET);
        } else if (node.isNumber()) {
            sb.append(ANSI_YELLOW).append(node.asText()).append(ANSI_RESET);
        } else if (node.isBoolean()) {
            sb.append(ANSI_PURPLE).append(node.asBoolean()).append(ANSI_RESET);
        } else if (node.isNull()) {
            sb.append(ANSI_RED).append("null").append(ANSI_RESET);
        } else {
            sb.append(node.asText());
        }
    }
}