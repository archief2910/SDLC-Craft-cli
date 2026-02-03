package com.sdlcraft.backend.sdlc;

/**
 * Phase represents the current phase in the Software Development Life Cycle.
 * 
 * The SDLC follows a linear progression from planning through production,
 * with each phase having specific entry and exit criteria.
 * 
 * Requirements: 3.1
 */
public enum Phase {
    /**
     * PLANNING: Initial project planning and requirements gathering.
     * Entry: Project initiation
     * Exit: Requirements documented and approved
     */
    PLANNING("Planning", "Initial project planning and requirements gathering"),
    
    /**
     * DEVELOPMENT: Active development and implementation.
     * Entry: Requirements approved
     * Exit: Code complete and unit tested
     */
    DEVELOPMENT("Development", "Active development and implementation"),
    
    /**
     * TESTING: Integration and system testing.
     * Entry: Code complete
     * Exit: All tests passing, quality gates met
     */
    TESTING("Testing", "Integration and system testing"),
    
    /**
     * STAGING: Pre-production validation and UAT.
     * Entry: Tests passing
     * Exit: Stakeholder approval for production
     */
    STAGING("Staging", "Pre-production validation and UAT"),
    
    /**
     * PRODUCTION: Live production environment.
     * Entry: Stakeholder approval
     * Exit: N/A (continuous operation)
     */
    PRODUCTION("Production", "Live production environment");
    
    private final String displayName;
    private final String description;
    
    Phase(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns the next phase in the SDLC progression.
     * 
     * @return the next phase, or null if already in PRODUCTION
     */
    public Phase next() {
        return switch (this) {
            case PLANNING -> DEVELOPMENT;
            case DEVELOPMENT -> TESTING;
            case TESTING -> STAGING;
            case STAGING -> PRODUCTION;
            case PRODUCTION -> null; // No next phase
        };
    }
    
    /**
     * Returns the previous phase in the SDLC progression.
     * 
     * @return the previous phase, or null if in PLANNING
     */
    public Phase previous() {
        return switch (this) {
            case PLANNING -> null; // No previous phase
            case DEVELOPMENT -> PLANNING;
            case TESTING -> DEVELOPMENT;
            case STAGING -> TESTING;
            case PRODUCTION -> STAGING;
        };
    }
    
    /**
     * Checks if transition to the target phase is valid.
     * 
     * Valid transitions:
     * - Forward progression (PLANNING → DEVELOPMENT → TESTING → STAGING → PRODUCTION)
     * - Backward rollback (any phase can go back to previous phase)
     * 
     * @param target the target phase
     * @return true if transition is valid, false otherwise
     */
    public boolean canTransitionTo(Phase target) {
        if (target == null) {
            return false;
        }
        
        // Can always stay in same phase
        if (this == target) {
            return true;
        }
        
        // Can move forward to next phase
        if (this.next() == target) {
            return true;
        }
        
        // Can rollback to previous phase
        if (this.previous() == target) {
            return true;
        }
        
        // Cannot skip phases
        return false;
    }
}
