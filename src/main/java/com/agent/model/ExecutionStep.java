package com.agent.model;

import java.util.Map;
import lombok.Data;

/**
 * Represents a single, concrete step within an {@link ExecutionPlan}.
 * Each step typically corresponds to a single API call.
 * <p>
 * Lombok's {@code @Data} annotation generates standard boilerplate code.
 */
@Data
public class ExecutionStep {

    /**
     * A unique identifier for this step within the plan (e.g., "step1", "step2").
     */
    private String stepId;

    /**
     * The {@code operationId} of the API operation to be executed in this step.
     * This links the step back to the {@link ApiSpecification}.
     */
    private String operationId;

    /**
     * The AI's reasoning for including this step in the plan, explaining its purpose.
     */
    private String reasoning;

    /**
     * A map defining how to obtain values for the parameters of the API operation.
     * The map's key is the parameter name, and the value is a {@link ParameterValueSource}
     * object describing where to get the value from.
     */
    private Map<String, ParameterValueSource> parameters;

    /**
     * A boolean flag indicating if this step should be executed in a loop.
     * (Note: This might be for future functionality, as the current implementation may not use it).
     */
    private boolean isLoop;
}