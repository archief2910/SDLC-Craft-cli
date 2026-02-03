package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Execution context passed between agents during workflow execution.
 * 
 * AgentContext maintains all state needed for agents to make decisions
 * and perform their work. It accumulates results as execution progresses
 * through the PLAN → ACT → OBSERVE → REFLECT cycle.
 * 
 * Design rationale:
 * - Immutable builder pattern prevents accidental state corruption
 * - Carries complete history for reflection and learning
 * - Deadline enables timeout handling
 * - Generic parameters map supports extensibility
 */
public class AgentContext {
    
    private final String executionId;
    private final IntentResult intent;
    private final SDLCState currentState;
    private final Map<String, Object> parameters;
    private final List<AgentResult> previousResults;
    private final LocalDateTime deadline;
    private final String userId;
    private final String projectId;
    
    private AgentContext(Builder builder) {
        this.executionId = builder.executionId;
        this.intent = builder.intent;
        this.currentState = builder.currentState;
        this.parameters = new HashMap<>(builder.parameters);
        this.previousResults = new ArrayList<>(builder.previousResults);
        this.deadline = builder.deadline;
        this.userId = builder.userId;
        this.projectId = builder.projectId;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public IntentResult getIntent() {
        return intent;
    }
    
    public SDLCState getCurrentState() {
        return currentState;
    }
    
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    public List<AgentResult> getPreviousResults() {
        return new ArrayList<>(previousResults);
    }
    
    public LocalDateTime getDeadline() {
        return deadline;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    /**
     * Check if execution has exceeded the deadline.
     */
    public boolean isExpired() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }
    
    /**
     * Get the most recent result from a specific agent type.
     */
    public AgentResult getLastResultByType(String agentType) {
        for (int i = previousResults.size() - 1; i >= 0; i--) {
            AgentResult result = previousResults.get(i);
            if (agentType.equals(result.getAgentType())) {
                return result;
            }
        }
        return null;
    }
    
    /**
     * Create a new context with an additional result.
     * Used to accumulate results as execution progresses.
     */
    public AgentContext withResult(AgentResult result) {
        return new Builder(this)
                .addResult(result)
                .build();
    }
    
    /**
     * Create a new context with updated parameters.
     */
    public AgentContext withParameters(Map<String, Object> newParameters) {
        return new Builder(this)
                .parameters(newParameters)
                .build();
    }
    
    public static class Builder {
        private String executionId;
        private IntentResult intent;
        private SDLCState currentState;
        private Map<String, Object> parameters = new HashMap<>();
        private List<AgentResult> previousResults = new ArrayList<>();
        private LocalDateTime deadline;
        private String userId;
        private String projectId;
        
        public Builder() {
        }
        
        public Builder(AgentContext context) {
            this.executionId = context.executionId;
            this.intent = context.intent;
            this.currentState = context.currentState;
            this.parameters = new HashMap<>(context.parameters);
            this.previousResults = new ArrayList<>(context.previousResults);
            this.deadline = context.deadline;
            this.userId = context.userId;
            this.projectId = context.projectId;
        }
        
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder intent(IntentResult intent) {
            this.intent = intent;
            return this;
        }
        
        public Builder currentState(SDLCState currentState) {
            this.currentState = currentState;
            return this;
        }
        
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
            return this;
        }
        
        public Builder parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        public Builder previousResults(List<AgentResult> previousResults) {
            this.previousResults = new ArrayList<>(previousResults);
            return this;
        }
        
        public Builder addResult(AgentResult result) {
            this.previousResults.add(result);
            return this;
        }
        
        public Builder deadline(LocalDateTime deadline) {
            this.deadline = deadline;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }
        
        public AgentContext build() {
            if (executionId == null) {
                throw new IllegalStateException("executionId is required");
            }
            if (intent == null) {
                throw new IllegalStateException("intent is required");
            }
            return new AgentContext(this);
        }
    }
}
