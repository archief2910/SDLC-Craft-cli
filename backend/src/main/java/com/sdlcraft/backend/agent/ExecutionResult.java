package com.sdlcraft.backend.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Final result of agent orchestration execution.
 * 
 * Contains all agent results, final status, and complete execution trace.
 * Used to communicate outcomes back to the CLI and for audit logging.
 */
public class ExecutionResult {
    
    private final String executionId;
    private final AgentStatus overallStatus;
    private final List<AgentResult> agentResults;
    private final Map<String, Object> finalData;
    private final String summary;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final long durationMs;
    
    private ExecutionResult(Builder builder) {
        this.executionId = builder.executionId;
        this.overallStatus = builder.overallStatus;
        this.agentResults = new ArrayList<>(builder.agentResults);
        this.finalData = new HashMap<>(builder.finalData);
        this.summary = builder.summary;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.durationMs = builder.durationMs;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public AgentStatus getOverallStatus() {
        return overallStatus;
    }
    
    public List<AgentResult> getAgentResults() {
        return new ArrayList<>(agentResults);
    }
    
    public Map<String, Object> getFinalData() {
        return new HashMap<>(finalData);
    }
    
    public Object getFinalData(String key) {
        return finalData.get(key);
    }
    
    public String getSummary() {
        return summary;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public boolean isSuccess() {
        return overallStatus == AgentStatus.SUCCESS;
    }
    
    public boolean isFailure() {
        return overallStatus == AgentStatus.FAILURE;
    }
    
    /**
     * Get results from a specific agent phase.
     */
    public List<AgentResult> getResultsByPhase(AgentPhase phase) {
        List<AgentResult> results = new ArrayList<>();
        for (AgentResult result : agentResults) {
            if (result.getPhase() == phase) {
                results.add(result);
            }
        }
        return results;
    }
    
    /**
     * Get the most recent result from a specific agent type.
     */
    public AgentResult getLastResultByType(String agentType) {
        for (int i = agentResults.size() - 1; i >= 0; i--) {
            AgentResult result = agentResults.get(i);
            if (agentType.equals(result.getAgentType())) {
                return result;
            }
        }
        return null;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String executionId;
        private AgentStatus overallStatus;
        private List<AgentResult> agentResults = new ArrayList<>();
        private Map<String, Object> finalData = new HashMap<>();
        private String summary;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long durationMs;
        
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder overallStatus(AgentStatus overallStatus) {
            this.overallStatus = overallStatus;
            return this;
        }
        
        public Builder agentResults(List<AgentResult> agentResults) {
            this.agentResults = new ArrayList<>(agentResults);
            return this;
        }
        
        public Builder addAgentResult(AgentResult result) {
            this.agentResults.add(result);
            return this;
        }
        
        public Builder finalData(Map<String, Object> finalData) {
            this.finalData = new HashMap<>(finalData);
            return this;
        }
        
        public Builder finalData(String key, Object value) {
            this.finalData.put(key, value);
            return this;
        }
        
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }
        
        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public ExecutionResult build() {
            if (executionId == null) {
                throw new IllegalStateException("executionId is required");
            }
            if (overallStatus == null) {
                throw new IllegalStateException("overallStatus is required");
            }
            return new ExecutionResult(this);
        }
    }
}
