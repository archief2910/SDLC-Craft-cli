package com.sdlcraft.backend.sdlc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SDLCState represents the current state of a project in the SDLC.
 * 
 * This is a value object that encapsulates all state information including
 * phase, risk level, metrics, and custom data. It is used for transferring
 * state information between layers.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
public class SDLCState {
    
    private String projectId;
    private Phase currentPhase;
    private RiskLevel riskLevel;
    private Double testCoverage;
    private Integer openIssues;
    private Integer totalIssues;
    private LocalDateTime lastDeployment;
    private Double releaseReadiness;
    private Map<String, Object> customMetrics;
    private LocalDateTime updatedAt;
    
    public SDLCState() {
        this.customMetrics = new HashMap<>();
    }
    
    public SDLCState(String projectId, Phase currentPhase) {
        this.projectId = projectId;
        this.currentPhase = currentPhase;
        this.customMetrics = new HashMap<>();
        this.updatedAt = LocalDateTime.now();
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
    
    public void addCustomMetric(String key, Object value) {
        this.customMetrics.put(key, value);
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "SDLCState{" +
                "projectId='" + projectId + '\'' +
                ", currentPhase=" + currentPhase +
                ", riskLevel=" + riskLevel +
                ", testCoverage=" + testCoverage +
                ", openIssues=" + openIssues +
                ", totalIssues=" + totalIssues +
                ", lastDeployment=" + lastDeployment +
                ", releaseReadiness=" + releaseReadiness +
                ", customMetrics=" + customMetrics +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
