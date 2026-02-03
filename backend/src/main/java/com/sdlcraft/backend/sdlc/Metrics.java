package com.sdlcraft.backend.sdlc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Metrics represents a set of measurements for a project.
 * 
 * This class encapsulates various metrics that are used to calculate
 * risk scores and release readiness.
 */
public class Metrics {
    
    private Double testCoverage;
    private Integer openIssues;
    private Integer totalIssues;
    private LocalDateTime lastDeployment;
    private Map<String, Object> customMetrics;
    
    public Metrics() {
        this.customMetrics = new HashMap<>();
    }
    
    // Getters and setters
    
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
    
    public Map<String, Object> getCustomMetrics() {
        return customMetrics;
    }
    
    public void setCustomMetrics(Map<String, Object> customMetrics) {
        this.customMetrics = customMetrics;
    }
    
    public void addCustomMetric(String key, Object value) {
        this.customMetrics.put(key, value);
    }
}
