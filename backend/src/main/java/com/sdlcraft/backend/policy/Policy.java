package com.sdlcraft.backend.policy;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;

/**
 * Policy represents a rule that must be checked before command execution.
 * 
 * Policies can be built-in or custom. Each policy has a name, description,
 * and a check method that determines if the policy is violated.
 * 
 * Examples:
 * - "No production deployments on Fridays"
 * - "Require code review before production deployment"
 * - "Test coverage must be > 80% for production deployment"
 */
public interface Policy {
    
    /**
     * Returns the policy name.
     * 
     * @return policy name
     */
    String getName();
    
    /**
     * Returns the policy description.
     * 
     * @return policy description
     */
    String getDescription();
    
    /**
     * Checks if the policy is violated.
     * 
     * @param intent the inferred intent
     * @param state the current SDLC state
     * @return policy violation if violated, null otherwise
     */
    PolicyViolation check(IntentResult intent, SDLCState state);
    
    /**
     * Returns the policy severity.
     * 
     * @return severity level (ERROR, WARNING, INFO)
     */
    PolicySeverity getSeverity();
}
