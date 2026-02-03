package com.sdlcraft.backend.sdlc;

/**
 * RiskLevel represents the assessed risk level for the current project state.
 * 
 * Risk is calculated based on multiple factors:
 * - Test coverage
 * - Open issues
 * - Time since last deployment
 * - Custom risk factors
 * 
 * Requirements: 3.2, 6.1
 */
public enum RiskLevel {
    /**
     * LOW: Minimal risk, safe to proceed.
     * - High test coverage (> 80%)
     * - Few open issues
     * - Recent successful deployments
     */
    LOW("Low", "Minimal risk, safe to proceed", 0),
    
    /**
     * MEDIUM: Moderate risk, proceed with caution.
     * - Moderate test coverage (60-80%)
     * - Some open issues
     * - Deployments within normal timeframe
     */
    MEDIUM("Medium", "Moderate risk, proceed with caution", 1),
    
    /**
     * HIGH: Significant risk, requires review.
     * - Low test coverage (40-60%)
     * - Many open issues
     * - Long time since last deployment
     */
    HIGH("High", "Significant risk, requires review", 2),
    
    /**
     * CRITICAL: Critical risk, do not proceed.
     * - Very low test coverage (< 40%)
     * - Critical open issues
     * - Very long time since last deployment
     */
    CRITICAL("Critical", "Critical risk, do not proceed", 3);
    
    private final String displayName;
    private final String description;
    private final int severity;
    
    RiskLevel(String displayName, String description, int severity) {
        this.displayName = displayName;
        this.description = description;
        this.severity = severity;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getSeverity() {
        return severity;
    }
    
    /**
     * Calculates risk level from a risk score (0.0 - 1.0).
     * 
     * @param riskScore the risk score (0.0 = no risk, 1.0 = maximum risk)
     * @return the corresponding risk level
     */
    public static RiskLevel fromScore(double riskScore) {
        if (riskScore < 0.25) {
            return LOW;
        } else if (riskScore < 0.5) {
            return MEDIUM;
        } else if (riskScore < 0.75) {
            return HIGH;
        } else {
            return CRITICAL;
        }
    }
    
    /**
     * Checks if this risk level is higher than another.
     * 
     * @param other the other risk level
     * @return true if this risk level is higher
     */
    public boolean isHigherThan(RiskLevel other) {
        return this.severity > other.severity;
    }
}
