package com.sdlcraft.backend.workflow;

import java.util.Map;

/**
 * Result of a single workflow step execution.
 */
public record StepResult(
    String stepId,
    String stepName,
    boolean success,
    String message,
    Map<String, Object> data
) {}

