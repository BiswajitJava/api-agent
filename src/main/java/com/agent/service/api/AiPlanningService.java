package com.agent.service.api;

import com.agent.model.ApiSpecification;
import com.agent.model.ExecutionPlan;

public interface AiPlanningService {

    /**
     * Translates a natural language prompt into an executable plan using the API specification.
     * @param prompt The user's natural language query.
     * @param spec The learned API specification.
     * @return An executable plan.
     */
    ExecutionPlan createExecutionPlan(String prompt, ApiSpecification spec);
}