package com.sdlcraft.backend.workflow;

import java.util.Map;

/**
 * A single step in a workflow.
 */
public record WorkflowStep(
    String id,
    String name,
    String integrationId,
    String action,
    Map<String, Object> parameters,
    boolean continueOnFailure,
    int maxRetries,
    String condition // SpEL expression for conditional execution
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String integrationId;
        private String action;
        private Map<String, Object> parameters = Map.of();
        private boolean continueOnFailure = false;
        private int maxRetries = 0;
        private String condition;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder integrationId(String integrationId) { this.integrationId = integrationId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public Builder continueOnFailure(boolean continueOnFailure) { this.continueOnFailure = continueOnFailure; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder condition(String condition) { this.condition = condition; return this; }
        
        public WorkflowStep build() {
            return new WorkflowStep(id, name, integrationId, action, parameters, continueOnFailure, maxRetries, condition);
        }
    }
}

