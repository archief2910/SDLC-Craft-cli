package com.sdlcraft.backend.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of a workflow execution.
 */
public record WorkflowResult(
    String workflowId,
    boolean success,
    List<StepResult> stepResults,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Map<String, Object> finalContext
) {
    public long durationMs() {
        return java.time.Duration.between(startTime, endTime).toMillis();
    }
    
    public String summary() {
        long passed = stepResults.stream().filter(StepResult::success).count();
        long failed = stepResults.stream().filter(r -> !r.success()).count();
        return String.format("Workflow %s: %d/%d steps passed in %dms",
            success ? "SUCCESS" : "FAILED", passed, stepResults.size(), durationMs());
    }
}

