package com.agent.cli;

import com.agent.dto.request.LearnApiRequest;
import com.agent.dto.response.CommandResponse;
import com.agent.service.api.OpenApiService;
import com.agent.service.api.StateService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * A Spring Shell component that provides commands for "learning" APIs from OpenAPI specifications.
 * This class is responsible for taking a user-provided API specification, parsing it, and storing it for later use.
 */
@ShellComponent
public class LearnCommand {

    private final OpenApiService openApiService;
    private final StateService stateService;

    /**
     * Constructs a new LearnCommand with the necessary services.
     *
     * @param openApiService The service used to load and parse OpenAPI specifications. [1, 2]
     * @param stateService   The service used to manage and persist state, including the learned API specifications. [7, 9]
     */
    public LearnCommand(OpenApiService openApiService, StateService stateService) {
        this.openApiService = openApiService;
        this.stateService = stateService;
    }

    /**
     * "Learns" an API by processing its OpenAPI specification from a given URL or file path.
     * The specification is loaded, parsed, and then saved using the StateService, associated with a user-defined alias.
     *
     * @param alias  A unique alias provided by the user to identify this API in subsequent commands.
     * @param source The URL or local file path pointing to the OpenAPI specification.
     * @return A string formatted with ANSI colors, indicating the success or failure of the learning process.
     */
    @ShellMethod(key = "learn", value = "Learns an API from an OpenAPI specification.")
    public String learn(
            @ShellOption(help = "A unique alias for this API.") String alias,
            @ShellOption(help = "The URL or file path of the OpenAPI spec.") String source
    ) {
        var request = new LearnApiRequest(alias, source);
        CommandResponse response;
        try {
            var spec = openApiService.loadAndParseSpec(request.source());
            stateService.saveSpecification(request.alias(), spec);
            response = new CommandResponse(true, "Successfully learned API '" + request.alias() + "'");
        } catch (Exception e) {
            response = new CommandResponse(false, "Failed to learn API: " + e.getMessage());
        }
        return response.toAnsiString();
    }
}