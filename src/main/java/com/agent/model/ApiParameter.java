package com.agent.model;

import lombok.Data;

@Data
public class ApiParameter {
    private String name;
    private String in; // "path", "query", "header"
    private boolean required;
    private Object schema;
}