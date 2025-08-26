package com.agent.service.api;

import com.agent.model.ExecutionPlan;
import com.fasterxml.jackson.databind.JsonNode;
import org.jline.reader.LineReader;

public interface ExecutionEngine {

    /**
     * Executes a given plan against the API specified by the alias.
     *
     * @param plan       The {@link ExecutionPlan} to execute.
     * @param alias      The alias of the API to target.
     * @param lineReader The JLine reader to prompt the user for input if required.
     * @param autofill   If true, the engine will automatically generate fake data for
     *                   request bodies instead of resolving them from the plan,
     *                   bypassing user prompts for body fields.
     * @return The {@link JsonNode} result from the final step of the plan.
     */
    JsonNode execute(ExecutionPlan plan, String alias, LineReader lineReader, boolean autofill);
}