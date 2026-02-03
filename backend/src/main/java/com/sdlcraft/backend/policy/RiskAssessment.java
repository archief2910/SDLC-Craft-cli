package com.sdlcraft.backend.policy;

import com.sdlcraft.backend.sdlc.RiskLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * RiskAssessment represents the assessed risk of executing a command.
 * 
 * This includes the risk level, specific concerns, explanation, and whether
 * user confirmation is required before execution.
 * 
 * Requirements: 6.1, 6.3
 */
public class RiskAssessment {
    
    private RiskLevel level;
    private List<String> concerns;
    private String explanation;
    private boolean requiresConfirmation;
    private String impactDescription;
    
    public RiskAssessment() {
        this.concerns = new ArrayList<>();
    }
    
    public RiskAssessment(RiskLevel level, String explanation) {
        this.level = level;
        this.explanation = explanation;
        this.concerns = new ArrayList<>();
        this.requiresConfirmation = level.isHigherThan(RiskLevel.MEDIUM);
    }
    
    // Getters and setters
    
    public RiskLevel getLevel() {
        return level;
    }
    
    public void setLevel(RiskLevel level) {
        this.level = level;
    }
    
    public List<String> getConcerns() {
        return concerns;
    }
    
    public void setConcerns(List<String> concerns) {
        this.concerns = concerns;
    }
    
    public void addConcern(String concern) {
        this.concerns.add(concern);
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }
    
    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }
    
    public String getImpactDescription() {
        return impactDescription;
    }
    
    public void setImpactDescription(String impactDescription) {
        this.impactDescription = impactDescription;
    }
    
    @Override
    public String toString() {
        return "RiskAssessment{" +
                "level=" + level +
                ", concerns=" + concerns +
                ", explanation='" + explanation + '\'' +
                ", requiresConfirmation=" + requiresConfirmation +
                ", impactDescription='" + impactDescription + '\'' +
                '}';
    }
}
