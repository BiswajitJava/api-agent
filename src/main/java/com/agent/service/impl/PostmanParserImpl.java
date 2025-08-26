package com.agent.service.impl;

import com.agent.exception.ApiAgentException;
import com.agent.model.*; // Make sure all models are imported
import com.agent.service.api.PostmanParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An implementation of the {@link PostmanParser} that reads a Postman collection
 * JSON file and transforms it into the agent's internal data models.
 * <p>
 * This parser extracts API operations, derives a base server URL, and crucially,
 * inspects the collection's 'auth' block to discover its authentication requirements.
 */
@Service
public class PostmanParserImpl implements PostmanParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     * <p>
     * This implementation reads the JSON file, recursively processes the 'item' array
     * to find all requests, and parses the 'auth' block at the root of the collection.
     * It then bundles the resulting {@link ApiSpecification} and {@link AuthDetails}
     * into a {@link ParsedPostmanCollection} object.
     */
    @Override
    public ParsedPostmanCollection parse(File file) {
        try {
            JsonNode root = objectMapper.readTree(file);
            ApiSpecification spec = new ApiSpecification();
            List<ApiOperation> operations = new ArrayList<>();

            // Recursively process items and folders to find all requests.
            processItems(root.path("item"), operations);

            // Try to extract a base URL from the first request found.
            if (!operations.isEmpty()) {
                JsonNode firstRequest = findFirstRequestNode(root.path("item"));
                if (firstRequest != null) {
                    String host = firstRequest.at("/url/host").asText();
                    // Handle Postman's {{baseUrl}} variable for the host
                    if (host.startsWith("{{") && host.endsWith("}}")) {
                        String variableName = host.substring(2, host.length() - 2);
                        String resolvedHost = resolveVariable(root, variableName);
                        host = resolvedHost != null ? resolvedHost : "localhost"; // Default if not found
                    }
                    String protocol = firstRequest.at("/url/protocol").asText("http");
                    spec.setServerUrls(Collections.singletonList(protocol + "://" + host));
                }
            }

            spec.setOperations(operations.stream().collect(Collectors.toMap(ApiOperation::getOperationId, op -> op, (oldOp, newOp) -> newOp)));
            spec.setSecuritySchemes(new HashMap<>()); // Keep this simple for Postman collections.

            // Parse the 'auth' block to find authentication details.
            Optional<AuthDetails> authDetails = parseAuth(root.path("auth"));

            // Return the correct wrapper object that satisfies the interface.
            return new ParsedPostmanCollection(spec, authDetails);

        } catch (IOException e) {
            throw new ApiAgentException("Failed to read or parse Postman collection file: " + file.getPath(), e);
        }
    }

    /**
     * Helper method to find the very first "request" node in a nested item structure.
     */
    private JsonNode findFirstRequestNode(JsonNode itemNode) {
        if (itemNode.isMissingNode()) return null;

        if (itemNode.isArray()) {
            for (JsonNode item : itemNode) {
                JsonNode found = findFirstRequestNode(item);
                if (found != null) return found;
            }
        } else if (itemNode.isObject()) {
            if (itemNode.has("request")) {
                return itemNode.get("request");
            }
            if (itemNode.has("item")) {
                return findFirstRequestNode(itemNode.get("item"));
            }
        }
        return null;
    }

    /**
     * Helper method to resolve a Postman variable's value from the "variable" array.
     */
    private String resolveVariable(JsonNode root, String variableName) {
        JsonNode variables = root.path("variable");
        if (variables.isArray()) {
            for (JsonNode variable : variables) {
                if (variable.path("key").asText("").equals(variableName)) {
                    return variable.path("value").asText(null);
                }
            }
        }
        return null;
    }

    /**
     * Parses the 'auth' block of a Postman collection.
     * @param authNode The JsonNode representing the auth block.
     * @return An Optional containing AuthDetails if supported auth is found.
     */
    private Optional<AuthDetails> parseAuth(JsonNode authNode) {
        if (!authNode.isObject()) {
            return Optional.empty();
        }
        String authType = authNode.path("type").asText();
        String token = null;

        switch (authType) {
            case "bearer":
                if (authNode.path("bearer").isArray() && authNode.path("bearer").has(0)) {
                    token = authNode.path("bearer").get(0).path("value").asText(null);
                }
                break;
            case "apikey":
                if (authNode.path("apikey").isArray() && authNode.path("apikey").has(0)) {
                    token = authNode.path("apikey").get(0).path("value").asText(null);
                }
                break;
            default:
                return Optional.empty();
        }
        return Optional.ofNullable(token).map(t -> new AuthDetails(authType, t));
    }


    private void processItems(JsonNode itemsNode, List<ApiOperation> operations) {
        if (!itemsNode.isArray()) return;
        StreamSupport.stream(itemsNode.spliterator(), false).forEach(item -> {
            if (item.has("request")) {
                operations.add(parseItemAsOperation(item));
            }
            if (item.has("item")) {
                processItems(item.path("item"), operations);
            }
        });
    }

    private ApiOperation parseItemAsOperation(JsonNode item) {
        ApiOperation op = new ApiOperation();
        JsonNode request = item.path("request");

        op.setOperationId(item.path("name").asText("unnamed-operation-" + UUID.randomUUID().toString().substring(0, 8)));
        op.setDescription(request.path("description").asText(""));
        op.setHttpMethod(request.path("method").asText());

        JsonNode url = request.path("url");
        op.setPath("/" + StreamSupport.stream(url.path("path").spliterator(), false)
                .map(JsonNode::asText)
                .toList()
                .stream()
                .map(p -> p.startsWith(":") ? "{" + p.substring(1) + "}" : p) // Convert Postman :id to {id}
                .collect(Collectors.joining("/")));

        List<ApiParameter> params = new ArrayList<>();
        if (url.path("query").isArray()) {
            StreamSupport.stream(url.path("query").spliterator(), false).forEach(q -> {
                ApiParameter param = new ApiParameter();
                param.setName(q.path("key").asText());
                param.setIn("query");
                param.setRequired(true);
                params.add(param);
            });
        }
        op.setParameters(params);

        if (request.path("body").path("mode").asText().equals("raw")) {
            Map<String, Object> bodySchema = new HashMap<>();
            bodySchema.put("type", "object");
            bodySchema.put("description", "Body from Postman example. Schema is not fully defined.");
            op.setRequestBodySchema(bodySchema);
        }
        return op;
    }
}