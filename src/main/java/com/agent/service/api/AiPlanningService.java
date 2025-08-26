package com.agent.service.api;

import com.agent.model.ApiSpecification;
import com.agent.model.ExecutionPlan;

public interface AiPlanningService {

    /**
     * Creates an execution plan based on a natural language prompt and an API specification.
     *
     * @param prompt The user's natural language goal.
     * @param spec   The specification of the API to be used.
     * @param autofill A flag to indicate if the AI should intelligently generate request bodies.
     * @return A structured ExecutionPlan.
     */
    ExecutionPlan createExecutionPlan(String prompt, ApiSpecification spec, boolean autofill);
}