package com.agent.cli;

import com.agent.dto.request.LearnApiRequest;
import com.agent.dto.response.CommandResponse;
import com.agent.service.api.OpenApiService;
import com.agent.service.api.StateService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class LearnCommand {

    private final OpenApiService openApiService;
    private final StateService stateService;

    // Injecting the INTERFACE, not the implementation
    public LearnCommand(OpenApiService openApiService, StateService stateService) {
        this.openApiService = openApiService;
        this.stateService = stateService;
    }

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