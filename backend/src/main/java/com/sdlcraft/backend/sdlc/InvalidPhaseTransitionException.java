package com.sdlcraft.backend.sdlc;

/**
 * Exception thrown when an invalid phase transition is attempted.
 */
public class InvalidPhaseTransitionException extends RuntimeException {
    
    private final Phase currentPhase;
    private final Phase targetPhase;
    
    public InvalidPhaseTransitionException(Phase currentPhase, Phase targetPhase) {
        super(String.format("Invalid phase transition from %s to %s", currentPhase, targetPhase));
        this.currentPhase = currentPhase;
        this.targetPhase = targetPhase;
    }
    
    public Phase getCurrentPhase() {
        return currentPhase;
    }
    
    public Phase getTargetPhase() {
        return targetPhase;
    }
}
