package com.agent.model;

import java.util.List;
import lombok.Data;

@Data
public class ExecutionPlan {
    private List<ExecutionStep> steps;
}