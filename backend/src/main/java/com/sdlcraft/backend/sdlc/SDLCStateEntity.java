package com.sdlcraft.backend.sdlc;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SDLCStateEntity is the JPA entity for persisting SDLC state to PostgreSQL.
 * 
 * This entity stores the current state of a project including phase, risk level,
 * metrics, and custom data. The custom metrics are stored as JSONB for flexibility.
 * 
 * Requirements: 3.1, 3.5, 9.3
 */
@Entity
@Table(name = "sdlc_state")
public class SDLCStateEntity {
    
    @Id
    @Column(name = "project_id", nullable = false)
    private String projectId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false)
    private Phase currentPhase;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;
    
    @Column(name = "test_coverage")
    private Double testCoverage;
    
    @Column(name = "open_issues")
    private Integer openIssues;
    
    @Column(name = "total_issues")
    private Integer totalIssues;
    
    @Column(name = "last_deployment")
    private LocalDateTime lastDeployment;
    
    @Column(name = "release_readiness")
    private Double releaseReadiness;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_metrics")
    private Map<String, Object> customMetrics = new HashMap<>();
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    
    public SDLCStateEntity() {
    }
    
    public SDLCStateEntity(String projectId, Phase currentPhase) {
        this.projectId = projectId;
        this.currentPhase = currentPhase;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Convert to/from SDLCState
    
    public SDLCState toSDLCState() {
        SDLCState state = new SDLCState(projectId, currentPhase);
        state.setRiskLevel(riskLevel);
        state.setTestCoverage(testCoverage);
        state.setOpenIssues(openIssues);
        state.setTotalIssues(totalIssues);
        state.setLastDeployment(lastDeployment);
        state.setReleaseReadiness(releaseReadiness);
        state.setCustomMetrics(new HashMap<>(customMetrics));
        state.setUpdatedAt(updatedAt);
        return state;
    }
    
    public static SDLCStateEntity fromSDLCState(SDLCState state) {
        SDLCStateEntity entity = new SDLCStateEntity(state.getProjectId(), state.getCurrentPhase());
        entity.setRiskLevel(state.getRiskLevel());
        entity.setTestCoverage(state.getTestCoverage());
        entity.setOpenIssues(state.getOpenIssues());
        entity.setTotalIssues(state.getTotalIssues());
        entity.setLastDeployment(state.getLastDeployment());
        entity.setReleaseReadiness(state.getReleaseReadiness());
        entity.setCustomMetrics(new HashMap<>(state.getCustomMetrics()));
        return entity;
    }
    
    // Getters and setters
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public Phase getCurrentPhase() {
        return currentPhase;
    }
    
    public void setCurrentPhase(Phase currentPhase) {
        this.currentPhase = currentPhase;
    }
    
    public RiskLevel getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }
    
    public Double getTestCoverage() {
        return testCoverage;
    }
    
    public void setTestCoverage(Double testCoverage) {
        this.testCoverage = testCoverage;
    }
    
    public Integer getOpenIssues() {
        return openIssues;
    }
    
    public void setOpenIssues(Integer openIssues) {
        this.openIssues = openIssues;
    }
    
    public Integer getTotalIssues() {
        return totalIssues;
    }
    
    public void setTotalIssues(Integer totalIssues) {
        this.totalIssues = totalIssues;
    }
    
    public LocalDateTime getLastDeployment() {
        return lastDeployment;
    }
    
    public void setLastDeployment(LocalDateTime lastDeployment) {
        this.lastDeployment = lastDeployment;
    }
    
    public Double getReleaseReadiness() {
        return releaseReadiness;
    }
    
    public void setReleaseReadiness(Double releaseReadiness) {
        this.releaseReadiness = releaseReadiness;
    }
    
    public Map<String, Object> getCustomMetrics() {
        return customMetrics;
    }
    
    public void setCustomMetrics(Map<String, Object> customMetrics) {
        this.customMetrics = customMetrics;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
