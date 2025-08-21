package com.agent.model;

import java.util.List;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import lombok.Data;

@Data
public class ApiOperation {
    private String operationId;
    private String httpMethod;
    private String path;
    private String description;
    private List<ApiParameter> parameters;
    private List<SecurityRequirement> security;
    private Object requestBodySchema;
}