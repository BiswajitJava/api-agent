package com.agent.model;

import java.util.Map;
import lombok.Data;

@Data
public class ExecutionStep {
    private String stepId;
    private String operationId;
    private String reasoning;
    private Map<String, ParameterValueSource> parameters;
    private boolean isLoop;
}