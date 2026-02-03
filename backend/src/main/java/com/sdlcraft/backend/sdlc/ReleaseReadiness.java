package com.sdlcraft.backend.sdlc;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the release readiness assessment for a project.
 * Includes the readiness score, status, and detailed factors.
 */
public class ReleaseReadiness {
    
    private final double score;
    private final ReadinessStatus status;
    private final List<String> readyFactors;
    private final List<String> blockingFactors;
    
    public ReleaseReadiness(double score, ReadinessStatus status, 
                           List<String> readyFactors, List<String> blockingFactors) {
        this.score = score;
        this.status = status;
        this.readyFactors = readyFactors != null ? new ArrayList<>(readyFactors) : new ArrayList<>();
        this.blockingFactors = blockingFactors != null ? new ArrayList<>(blockingFactors) : new ArrayList<>();
    }
    
    public double getScore() {
        return score;
    }
    
    public ReadinessStatus getStatus() {
        return status;
    }
    
    public List<String> getReadyFactors() {
        return new ArrayList<>(readyFactors);
    }
    
    public List<String> getBlockingFactors() {
        return new ArrayList<>(blockingFactors);
    }
    
    /**
     * Enum representing the overall readiness status
     */
    public enum ReadinessStatus {
        READY("Ready for release"),
        ALMOST_READY("Almost ready - minor issues"),
        NOT_READY("Not ready - significant issues"),
        BLOCKED("Blocked - critical issues");
        
        private final String description;
        
        ReadinessStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        /**
         * Determine status from score
         */
        public static ReadinessStatus fromScore(double score) {
            if (score >= 0.9) {
                return READY;
            } else if (score >= 0.7) {
                return ALMOST_READY;
            } else if (score >= 0.5) {
                return NOT_READY;
            } else {
                return BLOCKED;
            }
        }
    }
}
