package com.agent.model;

import lombok.Data;

@Data
public class ParameterValueSource {
    public enum Source {
        USER_INPUT, FROM_STEP, STATIC
    }
    private Source source;
    private String value;
    private String stepId;
    private String jsonPath;
}