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
 * This command orchestrates the process of creating an execution plan from a user's prompt,
 * asking for user confirmation, and then executing the plan to interact with the API.
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
     * This method first retrieves the API specification using the provided alias. It then uses
     * the AI planning service to generate a multistep execution plan based on the user's prompt.
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
            System.out.println(ANSI_PURPLE + "-- Verbose mode enabled --" + ANSI_RESET);
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
                    System.out.println(ANSI_YELLOW + "  " + step.getStepId() + ". " + step.getReasoning() + ANSI_RESET)
            );

            String confirmation = lineReader.readLine(ANSI_CYAN + "Execute this plan? [y/N]: " + ANSI_RESET);

            if ("y".equalsIgnoreCase(confirmation.trim())) {
                System.out.println("Executing plan...");
                JsonNode finalResult = executionEngine.execute(plan, alias, this.lineReader);
                // --- Use the new color-aware formatter ---
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
     * A helper method to format a Jackson JsonNode into a pretty-printed, colorized JSON string.
     *
     * @param node The JsonNode to format.
     * @return A formatted and colorized JSON string.
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
     * A recursive helper to build the colorized JSON string with proper indentation.
     *
     * @param node The current JsonNode to process.
     * @param sb The StringBuilder to append to.
     * @param indentLevel The current indentation level.
     */
    private void buildColoredJsonString(JsonNode node, StringBuilder sb, int indentLevel) {
        String indent = "  ".repeat(indentLevel);

        if (node.isObject()) {
            sb.append(ANSI_WHITE).append("{").append(ANSI_RESET).append("\n");
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sb.append(indent).append("  ")
                        .append(ANSI_CYAN).append("\"").append(field.getKey()).append("\"").append(ANSI_RESET) // Key in cyan
                        .append(": ");
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
            sb.append(ANSI_GREEN).append("\"").append(node.asText()).append("\"").append(ANSI_RESET); // String in green
        } else if (node.isNumber()) {
            sb.append(ANSI_YELLOW).append(node.asText()).append(ANSI_RESET); // Number in yellow
        } else if (node.isBoolean()) {
            sb.append(ANSI_PURPLE).append(node.asBoolean()).append(ANSI_RESET); // Boolean in purple
        } else if (node.isNull()) {
            sb.append(ANSI_RED).append("null").append(ANSI_RESET); // Null in red
        } else {
            sb.append(node.asText()); // Fallback for other types
        }
    }
}