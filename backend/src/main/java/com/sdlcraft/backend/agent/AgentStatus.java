package com.sdlcraft.backend.agent;

/**
 * Status of agent execution result.
 * 
 * Provides clear indication of whether the agent phase completed successfully,
 * failed, or achieved partial success.
 */
public enum AgentStatus {
    
    /**
     * Agent phase completed successfully with expected results.
     */
    SUCCESS,
    
    /**
     * Agent phase failed to complete or produced errors.
     */
    FAILURE,
    
    /**
     * Agent phase completed but with some issues or warnings.
     * Results may be usable but not ideal.
     */
    PARTIAL,
    
    /**
     * Agent phase was skipped (e.g., due to conditions not being met).
     */
    SKIPPED
}
