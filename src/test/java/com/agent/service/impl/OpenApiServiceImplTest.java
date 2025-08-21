package com.agent.service.impl;

import com.agent.model.ApiOperation;
import com.agent.model.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URL;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiServiceImplTest {

    private OpenApiServiceImpl openApiService;

    @BeforeEach
    void setUp() {
        openApiService = new OpenApiServiceImpl();
    }

    @Test
    void loadAndParseSpec_shouldParseValidSpec() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-openapi.json");
        assertThat(resource).isNotNull();
        // FIX: Convert URL to a clean, OS-agnostic file path
        String specPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();

        ApiSpecification spec = openApiService.loadAndParseSpec(specPath);

        assertThat(spec).isNotNull();
        assertThat(spec.getOperations()).hasSize(2);
        assertThat(spec.getSecuritySchemes()).hasSize(2);

        // Test an operation with a pre-defined operationId
        assertThat(spec.getOperations()).containsKey("getItemById");
        ApiOperation getItemOp = spec.getOperations().get("getItemById");
        assertThat(getItemOp.getPath()).isEqualTo("/items/{itemId}");
        assertThat(getItemOp.getSecurity()).hasSize(1);
        assertThat(getItemOp.getSecurity().get(0).containsKey("ApiKeyAuth")).isTrue();
        assertThat(getItemOp.getDescription()).isEqualTo("Get an item by its ID");

        // Test an operation where operationId must be generated
        assertThat(spec.getOperations()).containsKey("get_items");
        ApiOperation getItemsOp = spec.getOperations().get("get_items");
        assertThat(getItemsOp.getDescription()).isEqualTo("Get all items");
        assertThat(getItemsOp.getParameters()).hasSize(1);
        assertThat(getItemsOp.getParameters().get(0).getName()).isEqualTo("status");
        assertThat(getItemsOp.getSecurity().get(0).containsKey("BearerAuth")).isTrue();
    }

    @Test
    void loadAndParseSpec_shouldThrowExceptionForInvalidSource() {
        assertThrows(IllegalArgumentException.class, () -> {
            openApiService.loadAndParseSpec("non/existent/file.json");
        });
    }
}