package com.agent.cli;

import com.agent.dto.response.CommandResponse;
import com.agent.model.ApiOperation;
import com.agent.model.ApiSpecification;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Map;

/**
 * A Spring Shell component that provides commands for inspecting learned APIs.
 * This class allows users to view details about API operations, parameters,
 * request bodies, and security requirements.
 */
@ShellComponent
public class InspectCommand {

    // ANSI escape codes for coloring the output
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_PURPLE = "\u001B[35m";

    private final StateService stateService;
    private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public InspectCommand(StateService stateService) {
        this.stateService = stateService;
    }

    /**
     * Displays details for a learned API.
     * If an operationId is provided, it shows details for only that operation.
     * Otherwise, it lists all available operations.
     *
     * @param alias The alias of the API to inspect.
     * @param operationId (Optional) The specific operationId to get details for.
     */
    @ShellMethod(key = "details", value = "Show details and request fields for a learned API.")
    public void details(
            @ShellOption(help = "The alias of the API to inspect.") String alias,
            @ShellOption(value = {"--operation", "-o"}, help = "The specific operation ID to inspect.", defaultValue = ShellOption.NULL) String operationId
    ) {
        ApiSpecification spec = stateService.getSpecification(alias);
        if (spec == null) {
            System.out.println(new CommandResponse(false, "No API found with alias '" + alias + "'.").toAnsiString());
            return;
        }

        if (operationId != null) {
            // User wants details for a single operation
            ApiOperation operation = spec.getOperations().get(operationId);
            if (operation == null) {
                System.out.println(new CommandResponse(false, "Operation ID '" + operationId + "' not found in API '" + alias + "'.").toAnsiString());
                return;
            }
            System.out.println(ANSI_CYAN + "Details for Operation: " + ANSI_YELLOW + operationId + ANSI_RESET);
            printOperationDetails(operation);
        } else {
            // Original behavior: List all operations
            System.out.println(ANSI_CYAN + "Available Operations for API: " + ANSI_YELLOW + alias + ANSI_RESET);
            spec.getOperations().values().forEach(this::printOperationDetails);
        }
    }

    /**
     * A private helper method to print the details of a single ApiOperation.
     * This avoids code duplication and keeps the main `details` method clean.
     * @param op The ApiOperation to print.
     */
    private void printOperationDetails(ApiOperation op) {
        System.out.println("-".repeat(50));
        System.out.println(ANSI_GREEN + "Operation ID: " + ANSI_YELLOW + op.getOperationId() + ANSI_RESET);
        System.out.println("  " + ANSI_PURPLE + op.getHttpMethod() + ANSI_RESET + " " + op.getPath());
        System.out.println("  Description: " + op.getDescription());

        if (op.getParameters() != null && !op.getParameters().isEmpty()) {
            System.out.println(ANSI_CYAN + "  Parameters:" + ANSI_RESET);
            op.getParameters().forEach(p -> {
                System.out.println("    - " + p.getName() + " (in: " + p.getIn() + ", required: " + p.isRequired() + ")");
            });
        }

        if (op.getRequestBodySchema() != null) {
            System.out.println(ANSI_CYAN + "  Request Body Fields (application/json):" + ANSI_RESET);
            try {
                String schemaJson = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(op.getRequestBodySchema());
                for (String line : schemaJson.split("\n")) {
                    System.out.println("    " + line);
                }
            } catch (JsonProcessingException e) {
                System.out.println("    Could not format request body schema.");
            }
        }
    }

    /**
     * Displays the security schemes defined for a learned API, explaining how to authenticate.
     *
     * @param alias The alias of the API to inspect.
     */
    @ShellMethod(key = "auth-info", value = "Show authentication info for a learned API.")
    public void authInfo(@ShellOption(help = "The alias of the API to inspect.") String alias) {
        ApiSpecification spec = stateService.getSpecification(alias);
        if (spec == null) {
            System.out.println(new CommandResponse(false, "No API found with alias '" + alias + "'.").toAnsiString());
            return;
        }

        Map<String, SecurityScheme> securitySchemes = spec.getSecuritySchemes();
        if (securitySchemes == null || securitySchemes.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No security schemes defined for API '" + alias + "'." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + "Authentication Information for API: " + ANSI_YELLOW + alias + ANSI_RESET);
        securitySchemes.forEach((name, scheme) -> {
            System.out.println("-".repeat(50));
            System.out.println(ANSI_GREEN + "Scheme Name: " + ANSI_YELLOW + name + ANSI_RESET);
            System.out.println("  Type: " + scheme.getType());

            // --- FULLY IMPLEMENTED SWITCH STATEMENT ---
            switch (scheme.getType()) {
                case APIKEY:
                    System.out.println("  Location: " + scheme.getIn());
                    System.out.println("  Header/Parameter Name: " + scheme.getName());
                    System.out.println(ANSI_CYAN + "  How to use: Use the 'auth' command with type 'api_key' and your token." + ANSI_RESET);
                    break;
                case HTTP:
                    System.out.println("  Scheme: " + scheme.getScheme());
                    if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                        System.out.println(ANSI_CYAN + "  How to use: Use the 'auth' command with type 'api_key' and your bearer token." + ANSI_RESET);
                    }
                    break;
                // Add cases for OAUTH2, OPENIDCONNECT etc. if you want to expand support later
                default:
                    System.out.println("  (Details for this authentication type are not fully displayed.)");
            }
        });
    }
}