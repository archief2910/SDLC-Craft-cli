package com.sdlcraft.backend.memory;

/**
 * Statistics about command executions for a project.
 * 
 * Provides insights into execution patterns and success rates.
 */
public class ExecutionStatistics {
    
    private final String projectId;
    private final long totalExecutions;
    private final long successfulExecutions;
    private final long failedExecutions;
    private final long partialExecutions;
    private final double successRate;
    private final long averageDurationMs;
    private final String mostCommonIntent;
    
    public ExecutionStatistics(String projectId, long totalExecutions, long successfulExecutions,
                             long failedExecutions, long partialExecutions, double successRate,
                             long averageDurationMs, String mostCommonIntent) {
        this.projectId = projectId;
        this.totalExecutions = totalExecutions;
        this.successfulExecutions = successfulExecutions;
        this.failedExecutions = failedExecutions;
        this.partialExecutions = partialExecutions;
        this.successRate = successRate;
        this.averageDurationMs = averageDurationMs;
        this.mostCommonIntent = mostCommonIntent;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public long getTotalExecutions() {
        return totalExecutions;
    }
    
    public long getSuccessfulExecutions() {
        return successfulExecutions;
    }
    
    public long getFailedExecutions() {
        return failedExecutions;
    }
    
    public long getPartialExecutions() {
        return partialExecutions;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    public long getAverageDurationMs() {
        return averageDurationMs;
    }
    
    public String getMostCommonIntent() {
        return mostCommonIntent;
    }
}
