package com.sdlcraft.backend.agent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Result returned by an agent after executing a phase.
 * 
 * AgentResult encapsulates the outcome of agent execution including
 * success/failure status, data produced, reasoning, and any errors.
 * 
 * Design rationale:
 * - Status enum provides clear success/failure/partial states
 * - Data map allows flexible result types without rigid schemas
 * - Reasoning field supports explainability requirements
 * - Error details enable proper error handling and recovery
 */
public class AgentResult {
    
    private final String agentType;
    private final AgentPhase phase;
    private final AgentStatus status;
    private final Map<String, Object> data;
    private final String reasoning;
    private final String error;
    private final LocalDateTime timestamp;
    
    private AgentResult(Builder builder) {
        this.agentType = builder.agentType;
        this.phase = builder.phase;
        this.status = builder.status;
        this.data = new HashMap<>(builder.data);
        this.reasoning = builder.reasoning;
        this.error = builder.error;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }
    
    public String getAgentType() {
        return agentType;
    }
    
    public AgentPhase getPhase() {
        return phase;
    }
    
    public AgentStatus getStatus() {
        return status;
    }
    
    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }
    
    public Object getData(String key) {
        return data.get(key);
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public String getError() {
        return error;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public boolean isSuccess() {
        return status == AgentStatus.SUCCESS;
    }
    
    public boolean isFailure() {
        return status == AgentStatus.FAILURE;
    }
    
    public boolean isPartial() {
        return status == AgentStatus.PARTIAL;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String agentType;
        private AgentPhase phase;
        private AgentStatus status;
        private Map<String, Object> data = new HashMap<>();
        private String reasoning;
        private String error;
        private LocalDateTime timestamp;
        
        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }
        
        public Builder phase(AgentPhase phase) {
            this.phase = phase;
            return this;
        }
        
        public Builder status(AgentStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder data(Map<String, Object> data) {
            this.data = new HashMap<>(data);
            return this;
        }
        
        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }
        
        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }
        
        public Builder error(String error) {
            this.error = error;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public AgentResult build() {
            if (agentType == null) {
                throw new IllegalStateException("agentType is required");
            }
            if (phase == null) {
                throw new IllegalStateException("phase is required");
            }
            if (status == null) {
                throw new IllegalStateException("status is required");
            }
            return new AgentResult(this);
        }
    }
}
