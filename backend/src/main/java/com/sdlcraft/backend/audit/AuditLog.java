package com.sdlcraft.backend.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit log entity for tracking all significant system events.
 * 
 * Records state changes, high-risk command confirmations, agent executions,
 * and other important events for compliance and debugging.
 * 
 * Design rationale:
 * - Immutable after creation (no setters for core fields)
 * - JSONB for flexible old/new value storage
 * - Action type enum for categorization
 * - Risk level tracking for security analysis
 * - User and project context for filtering
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String projectId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;
    
    @Column(nullable = false)
    private String entityType;
    
    private String entityId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values")
    private Map<String, Object> oldValues;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values")
    private Map<String, Object> newValues;
    
    @Column(columnDefinition = "text")
    private String description;
    
    @Enumerated(EnumType.STRING)
    private AuditRiskLevel riskLevel;
    
    private Boolean requiresConfirmation;
    
    private Boolean wasConfirmed;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private String ipAddress;
    
    private String userAgent;
    
    public AuditLog() {
        this.oldValues = new HashMap<>();
        this.newValues = new HashMap<>();
        this.metadata = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters only (immutable after creation)
    
    public String getId() {
        return id;
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
    
    public AuditAction getAction() {
        return action;
    }
    
    public void setAction(AuditAction action) {
        this.action = action;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    public Map<String, Object> getOldValues() {
        return oldValues;
    }
    
    public void setOldValues(Map<String, Object> oldValues) {
        this.oldValues = oldValues;
    }
    
    public Map<String, Object> getNewValues() {
        return newValues;
    }
    
    public void setNewValues(Map<String, Object> newValues) {
        this.newValues = newValues;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public AuditRiskLevel getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(AuditRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }
    
    public Boolean getRequiresConfirmation() {
        return requiresConfirmation;
    }
    
    public void setRequiresConfirmation(Boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }
    
    public Boolean getWasConfirmed() {
        return wasConfirmed;
    }
    
    public void setWasConfirmed(Boolean wasConfirmed) {
        this.wasConfirmed = wasConfirmed;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    /**
     * Action types for audit logging.
     */
    public enum AuditAction {
        STATE_CHANGE,           // SDLC state transition
        COMMAND_EXECUTED,       // Command execution
        CONFIRMATION_REQUIRED,  // High-risk command requiring confirmation
        CONFIRMATION_GRANTED,   // User confirmed high-risk action
        CONFIRMATION_DENIED,    // User denied high-risk action
        AGENT_EXECUTION,        // Agent workflow execution
        POLICY_VIOLATION,       // Policy check failed
        INTENT_INFERRED,        // Intent inference completed
        CONTEXT_STORED,         // Project context stored
        METRIC_UPDATED,         // Metrics updated
        CUSTOM                  // Custom audit event
    }
    
    /**
     * Risk levels for audit events.
     */
    public enum AuditRiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
