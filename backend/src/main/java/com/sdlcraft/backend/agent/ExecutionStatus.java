package com.sdlcraft.backend.agent;

import java.time.LocalDateTime;

/**
 * Status of an ongoing or completed execution.
 * 
 * Provides visibility into execution progress for monitoring and cancellation.
 */
public class ExecutionStatus {
    
    private final String executionId;
    private final ExecutionState state;
    private final String currentAgent;
    private final AgentPhase currentPhase;
    private final int percentComplete;
    private final LocalDateTime startTime;
    private final String message;
    
    public ExecutionStatus(String executionId, ExecutionState state, String currentAgent,
                          AgentPhase currentPhase, int percentComplete, LocalDateTime startTime,
                          String message) {
        this.executionId = executionId;
        this.state = state;
        this.currentAgent = currentAgent;
        this.currentPhase = currentPhase;
        this.percentComplete = percentComplete;
        this.startTime = startTime;
        this.message = message;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public ExecutionState getState() {
        return state;
    }
    
    public String getCurrentAgent() {
        return currentAgent;
    }
    
    public AgentPhase getCurrentPhase() {
        return currentPhase;
    }
    
    public int getPercentComplete() {
        return percentComplete;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isRunning() {
        return state == ExecutionState.RUNNING;
    }
    
    public boolean isComplete() {
        return state == ExecutionState.COMPLETED || state == ExecutionState.FAILED;
    }
    
    public enum ExecutionState {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
