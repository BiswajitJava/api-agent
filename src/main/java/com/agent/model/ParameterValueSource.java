package com.agent.model;

import lombok.Data;

/**
 * Defines the source for a parameter's value in an {@link ExecutionStep}.
 * This class allows the execution plan to specify whether a parameter's value should
 * come from user input, a static value, or the output of a previous step.
 * <p>
 * Lombok's {@code @Data} annotation generates standard boilerplate code.
 */
@Data
public class ParameterValueSource {

    /**
     * An enumeration of the possible sources for a parameter's value.
     */
    public enum Source {
        /**
         * The value must be prompted from the user at execution time.
         */
        USER_INPUT,
        /**
         * The value is extracted from the JSON response of a previous execution step.
         */
        FROM_STEP,
        /**
         * The value is a hardcoded, static string defined in the plan.
         */
        STATIC
    }

    /**
     * The source type, indicating which fields in this object are relevant.
     */
    private Source source;

    /**
     * The static value for the parameter. Can be a String, Number, or a Map/List
     * representing a JSON object/array. Only used when {@code source} is {@code STATIC}.
     */
    private Object value;

    /**
     * The {@code stepId} of the preceding step to get the value from.
     * Only used when {@code source} is {@code FROM_STEP}.
     */
    private String stepId;

    /**
     * The JSONPath expression to extract the desired value from the previous step's response.
     * Only used when {@code source} is {@code FROM_STEP}.
     */
    private String jsonPath;
}