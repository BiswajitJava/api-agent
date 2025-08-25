package com.agent.service.impl;

import com.agent.exception.ApiAgentException;
import com.agent.model.ApiOperation;
import com.agent.model.ApiParameter;
import com.agent.model.ApiSpecification;
import com.agent.service.api.OpenApiService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OpenApiServiceImpl implements OpenApiService {

    /**
     * {@inheritDoc}
     * This implementation uses the swagger-parser library to load and parse the spec.
     * It then iterates through all paths and operations, converting them into a simplified
     * internal model (ApiSpecification) that is easier for the AI to process.
     * The key feature is its ability to resolve $ref references within the schema.
     */
    @Override
    public ApiSpecification loadAndParseSpec(String source) {
        log.info("Loading and parsing OpenAPI spec from: {}", source);
        OpenAPI openAPI = new OpenAPIV3Parser().read(source);
        if (openAPI == null) {
            throw new ApiAgentException("Failed to load or parse the OpenAPI specification from the source: " + source);
        }

        ApiSpecification spec = new ApiSpecification();
        spec.setServerUrls(openAPI.getServers().stream().map(s -> s.getUrl()).collect(Collectors.toList()));

        if (openAPI.getComponents() != null) {
            spec.setSecuritySchemes(openAPI.getComponents().getSecuritySchemes());
        }

        Map<String, ApiOperation> operations = openAPI.getPaths().entrySet().stream()
                .flatMap(entry -> {
                    String path = entry.getKey();
                    PathItem pathItem = entry.getValue();
                    return pathItem.readOperationsMap().entrySet().stream().map(opEntry ->
                            // Pass the root openAPI object to resolve references
                            createApiOperation(opEntry.getKey(), opEntry.getValue(), path, openAPI)
                    );
                })
                .collect(Collectors.toMap(ApiOperation::getOperationId, op -> op, (oldOp, newOp) -> newOp));

        spec.setOperations(operations);
        log.info("Successfully parsed {} operations from the specification.", operations.size());
        return spec;
    }

    private ApiOperation createApiOperation(PathItem.HttpMethod method, io.swagger.v3.oas.models.Operation operation, String path, OpenAPI openAPI) {
        ApiOperation apiOp = new ApiOperation();

        // Use the operationId if present, otherwise generate a predictable one
        String operationId = operation.getOperationId() != null
                ? operation.getOperationId()
                : generateOperationId(method.name(), path);

        apiOp.setOperationId(operationId);
        apiOp.setHttpMethod(method.name());
        apiOp.setPath(path);
        apiOp.setDescription(operation.getSummary() != null ? operation.getSummary() : operation.getDescription());
        apiOp.setSecurity(operation.getSecurity());

        if (operation.getParameters() != null) {
            apiOp.setParameters(operation.getParameters().stream()
                    .map(p -> createApiParameter(p, openAPI))
                    .collect(Collectors.toList()));
        } else {
            apiOp.setParameters(Collections.emptyList());
        }

        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().getContent().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("application/json"))
                    .findFirst()
                    .ifPresent(jsonEntry -> {
                        Schema<?> schema = jsonEntry.getValue().getSchema();
                        apiOp.setRequestBodySchema(schemaToMap(schema, openAPI));
                    });
        }
        return apiOp;
    }

    private ApiParameter createApiParameter(Parameter parameter, OpenAPI openAPI) {
        ApiParameter apiParam = new ApiParameter();
        apiParam.setName(parameter.getName());
        apiParam.setIn(parameter.getIn());
        apiParam.setRequired(parameter.getRequired() != null && parameter.getRequired());
        if (parameter.getSchema() != null) {
            apiParam.setSchema(schemaToMap(parameter.getSchema(), openAPI));
        }
        return apiParam;
    }

    /**
     * Recursively converts a complex Swagger Schema into a simplified Map.
     * This is the core of the fix, as it now correctly handles $ref references.
     *
     * @param schema The schema to process.
     * @param openAPI The root OpenAPI object, used to look up references.
     * @return A simplified Map representing the schema.
     */
    private Map<String, Object> schemaToMap(Schema<?> schema, OpenAPI openAPI) {
        if (schema == null) {
            return Collections.emptyMap();
        }

        // --- THIS IS THE KEY FIX ---
        // If the schema is a reference, find it in the components and parse it recursively.
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            if (ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring("#/components/schemas/".length());
                Schema<?> resolvedSchema = openAPI.getComponents().getSchemas().get(schemaName);
                if (resolvedSchema != null) {
                    // Recursively call this method with the actual schema definition
                    return schemaToMap(resolvedSchema, openAPI);
                }
            }
            // If the reference cannot be resolved, just return its path.
            return Map.of("$ref", ref);
        }
        // --- END OF FIX ---

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", schema.getType());
        if (schema.getRequired() != null) {
            map.put("required_fields", schema.getRequired());
        }
        if (schema.getDescription() != null) {
            map.put("description", schema.getDescription());
        }
        if (schema.getEnum() != null) {
            map.put("enum", schema.getEnum());
        }

        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            Map<String, Object> properties = new LinkedHashMap<>();
            schema.getProperties().forEach((key, value) -> properties.put((String) key, schemaToMap((Schema<?>) value, openAPI)));
            map.put("properties", properties);
        }

        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            map.put("items", schemaToMap(schema.getItems(), openAPI));
        }

        return map;
    }

    private String generateOperationId(String httpMethod, String path) {
        String sanitizedPath = path
                .replaceAll("\\{", "by_")
                .replaceAll("[{}/]", "_")
                .replaceAll("__", "_")
                .replaceAll("^_|_$", "");
        return httpMethod.toLowerCase() + "_" + sanitizedPath;
    }
}