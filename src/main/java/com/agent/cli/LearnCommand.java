package com.agent.cli;

import com.agent.dto.request.LearnApiRequest;
import com.agent.dto.response.CommandResponse;
import com.agent.service.api.OpenApiService;
import com.agent.service.api.PostmanParser;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.File;

/**
 * A Spring Shell component that provides commands for "learning" APIs from various sources.
 * This class is responsible for taking a user-provided API specification
 * (either OpenAPI or a Postman Collection), parsing it, and storing it for later use.
 */
@ShellComponent
public class LearnCommand {

    private final OpenApiService openApiService;
    private final StateService stateService;
    private final PostmanParser postmanParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new LearnCommand with the necessary services for parsing different spec types.
     *
     * @param openApiService The service used to load and parse OpenAPI specifications.
     * @param stateService   The service used to manage and persist state.
     * @param postmanParser  The service used to load and parse Postman collections.
     */
    public LearnCommand(OpenApiService openApiService, StateService stateService, PostmanParser postmanParser) {
        this.openApiService = openApiService;
        this.stateService = stateService;
        this.postmanParser = postmanParser;
    }

    /**
     * "Learns" an API by processing its specification from a given URL or file path.
     * <p>
     * This command intelligently detects whether the source is an OpenAPI specification
     * or a Postman collection (from a local file). It then uses the appropriate parser
     * to load the spec and saves the result using the StateService, associated with a
     * user-defined alias.
     *
     * @param alias  A unique alias provided by the user to identify this API in subsequent commands.
     * @param source The URL (for OpenAPI) or local file path (for OpenAPI or Postman collections) of the specification.
     * @return A string formatted with ANSI colors, indicating the success or failure of the learning process.
     */
    @ShellMethod(key = "learn", value = "Learns an API from an OpenAPI spec or Postman collection.")
    public String learn(
            @ShellOption(help = "A unique alias for this API.") String alias,
            @ShellOption(help = "The URL or file path of the OpenAPI spec or Postman collection.") String source
    ) {
        var request = new LearnApiRequest(alias, source);
        CommandResponse response;
        try {
            if (isPostmanCollection(source)) {
                // The parser returns a ParsedPostmanCollection object. We need to get the spec from it.
                var parsedCollection = postmanParser.parse(new File(source));
                stateService.saveSpecification(request.alias(), parsedCollection.spec());
                response = new CommandResponse(true, "Successfully learned API '" + request.alias() + "' from Postman collection.");
            } else {
                var spec = openApiService.loadAndParseSpec(request.source());
                stateService.saveSpecification(request.alias(), spec);
                response = new CommandResponse(true, "Successfully learned API '" + request.alias() + "' from OpenAPI spec.");
            }
        } catch (Exception e) {
            response = new CommandResponse(false, "Failed to learn API: " + e.getMessage());
        }
        return response.toAnsiString();
    }

    /**
     * A private helper method to detect if a local file is likely a Postman collection.
     *
     * @param source The local file path to check.
     * @return {@code true} if the file is a Postman collection, {@code false} otherwise.
     */
    private boolean isPostmanCollection(String source) {
        File file = new File(source);
        if (!file.exists() || source.startsWith("http")) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(file);
            return root.has("info") && root.has("item");
        } catch (Exception e) {
            return false;
        }
    }
}