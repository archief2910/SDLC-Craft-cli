package com.sdlcraft.backend.memory;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a command execution in long-term memory.
 * 
 * Stores complete execution history including command, outcome, and timing.
 */
@Entity
@Table(name = "command_executions")
public class CommandExecution {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String projectId;
    
    @Column(nullable = false, columnDefinition = "text")
    private String rawCommand;
    
    @Column(nullable = false)
    private String intent;
    
    private String target;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modifiers")
    private Map<String, String> modifiers;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;
    
    @Column(columnDefinition = "text")
    private String outcome;
    
    @Column(columnDefinition = "text")
    private String reasoning;
    
    @Column(nullable = false)
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private Long durationMs;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;
    
    public CommandExecution() {
        this.modifiers = new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public String getRawCommand() {
        return rawCommand;
    }
    
    public void setRawCommand(String rawCommand) {
        this.rawCommand = rawCommand;
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
    
    public ExecutionStatus getStatus() {
        return status;
    }
    
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }
    
    public String getOutcome() {
        return outcome;
    }
    
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public Long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public enum ExecutionStatus {
        SUCCESS,
        FAILURE,
        PARTIAL,
        CANCELLED
    }
}
