package com.agent.cli;

import com.agent.dto.request.AuthRequest;
import com.agent.dto.response.CommandResponse;
import com.agent.service.api.StateService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AuthCommand {

    private final StateService stateService;

    public AuthCommand(StateService stateService) {
        this.stateService = stateService;
    }

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