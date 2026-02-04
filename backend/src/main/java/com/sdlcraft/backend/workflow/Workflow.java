package com.sdlcraft.backend.workflow;

import java.util.List;
import java.util.Map;

/**
 * Definition of an SDLC workflow.
 * Workflows are sequences of steps that can span multiple integrations.
 */
public record Workflow(
    String id,
    String name,
    String description,
    List<WorkflowStep> steps,
    Map<String, Object> defaultConfig
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private List<WorkflowStep> steps;
        private Map<String, Object> defaultConfig = Map.of();
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder steps(List<WorkflowStep> steps) { this.steps = steps; return this; }
        public Builder defaultConfig(Map<String, Object> config) { this.defaultConfig = config; return this; }
        
        public Workflow build() {
            return new Workflow(id, name, description, steps, defaultConfig);
        }
    }
}

