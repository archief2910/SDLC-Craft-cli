package com.sdlcraft.backend.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for persisting agent execution traces.
 * 
 * Stores complete execution history for debugging, learning, and audit purposes.
 * Enables analysis of execution patterns and failure modes over time.
 */
@Entity
@Table(name = "agent_executions")
public class AgentExecutionEntity {
    
    @Id
    private String executionId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String projectId;
    
    @Column(nullable = false)
    private String intent;
    
    private String target;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modifiers")
    private Map<String, String> modifiers;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus overallStatus;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agent_results")
    private Map<String, Object> agentResults;
    
    @Column(columnDefinition = "text")
    private String summary;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    private Long durationMs;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context")
    private Map<String, Object> context;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    public AgentExecutionEntity() {
        this.modifiers = new HashMap<>();
        this.agentResults = new HashMap<>();
        this.context = new HashMap<>();
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
    
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getIntent() {
        return intent;
    }
    
    public void setIntent(String intent) {
        this.intent = intent;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public Map<String, String> getModifiers() {
        return modifiers;
    }
    
    public void setModifiers(Map<String, String> modifiers) {
        this.modifiers = modifiers;
    }
    
    public AgentStatus getOverallStatus() {
        return overallStatus;
    }
    
    public void setOverallStatus(AgentStatus overallStatus) {
        this.overallStatus = overallStatus;
    }
    
    public Map<String, Object> getAgentResults() {
        return agentResults;
    }
    
    public void setAgentResults(Map<String, Object> agentResults) {
        this.agentResults = agentResults;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public Long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
