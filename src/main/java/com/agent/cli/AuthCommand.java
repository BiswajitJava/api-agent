package com.agent.cli;

import com.agent.dto.request.AuthRequest;
import com.agent.dto.response.CommandResponse;
import com.agent.service.api.StateService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * A Spring Shell component that provides commands for handling authentication. [1]
 * This class is responsible for processing user input related to authentication configuration for APIs.
 */
@ShellComponent
public class AuthCommand {

    private final StateService stateService;

    /**
     * Constructs a new AuthCommand with the given StateService.
     *
     * @param stateService The service used to manage and persist state, including credentials.
     */
    public AuthCommand(StateService stateService) {
        this.stateService = stateService;
    }

    /**
     * Configures authentication for a specific API by saving its credentials. [2]
     * This shell method allows users to set the authentication type and token for a given API alias.
     * Currently, it only supports the 'api_key' authentication type.
     *
     * @param alias The alias of the API for which to configure authentication. This is a unique identifier for the API.
     * @param type  The type of authentication to be used (e.g., 'api_key').
     * @param token The authentication token or credential.
     * @return A string formatted with ANSI colors indicating the result of the operation.
     */
    @ShellMethod(key = "auth", value = "Configure authentication for an API.")
    public String auth(
            @ShellOption(help = "The alias of the API to authenticate.") String alias,
            @ShellOption(help = "The authentication type (e.g., 'api_key').") String type,
            @ShellOption(help = "The credential or token.") String token
    ) {
        var request = new AuthRequest(alias, type, token);
        CommandResponse response;

        if (!"api_key".equalsIgnoreCase(request.type())) {
            response = new CommandResponse(false, "Unsupported authentication type: " + request.type());
        } else {
            stateService.saveCredential(request.alias(), request.token());
            response = new CommandResponse(true, "Successfully configured authentication for '" + request.alias() + "'");
        }
        return response.toAnsiString();
    }
}