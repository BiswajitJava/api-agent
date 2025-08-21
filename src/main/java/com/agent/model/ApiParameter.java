package com.agent.model;

import lombok.Data;

/**
 * Represents a single parameter for an {@link ApiOperation}.
 * This class models the details of a parameter, such as its name, location (path, query, etc.),
 * and schema.
 * <p>
 * Lombok's {@code @Data} annotation generates standard boilerplate code.
 */
@Data
public class ApiParameter {

    /**
     * The name of the parameter.
     */
    private String name;

    /**
     * The location of the parameter. Common values are "path", "query", or "header".
     */
    private String in;

    /**
     * A boolean flag indicating whether the parameter is required for the operation to succeed.
     */
    private boolean required;

    /**
     * The schema defining the data type and constraints of the parameter.
     * Stored as a generic {@code Object} to handle various schema structures.
     */
    private Object schema;
}