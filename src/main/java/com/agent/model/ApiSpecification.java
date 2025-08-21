package com.agent.model;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.Data;

@Data
public class ApiSpecification {
    private Map<String, ApiOperation> operations;
    private Map<String, SecurityScheme> securitySchemes;
    private List<String> serverUrls;
}