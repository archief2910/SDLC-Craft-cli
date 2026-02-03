package com.sdlcraft.backend.policy;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;

import java.util.List;

/**
 * PolicyEngine enforces safety policies and determines confirmation requirements.
 * 
 * The policy engine is responsible for:
 * - Classifying commands by risk level
 * - Determining if confirmation is required
 * - Checking policy violations
 * - Generating impact assessments
 * 
 * This ensures that high-risk operations (production deployments, deletions, resets)
 * require explicit user confirmation and are properly logged.
 * 
 * Requirements: 6.1, 6.2, 6.5, 6.6
 */
public interface PolicyEngine {
    
    /**
     * Assesses the risk of executing an intent in the current project state.
     * 
     * Risk assessment considers:
     * - Intent type (release, improve, etc.)
     * - Target environment (production, staging, etc.)
     * - Current SDLC state (phase, risk level, coverage)
     * - Historical execution patterns
     * 
     * @param intent the inferred intent
     * @param state the current SDLC state
     * @return risk assessment with level, concerns, and explanation
     */
    RiskAssessment assessRisk(IntentResult intent, SDLCState state);
    
    /**
     * Determines if the given risk assessment requires user confirmation.
     * 
     * Confirmation is required for:
     * - HIGH or CRITICAL risk operations
     * - Operations on production environment
     * - Destructive operations (delete, reset)
     * 
     * @param assessment the risk assessment
     * @return true if confirmation is required, false otherwise
     */
    boolean requiresConfirmation(RiskAssessment assessment);
    
    /**
     * Checks if the intent violates any registered policies.
     * 
     * Policies can be:
     * - Built-in (e.g., "no production deployments on Fridays")
     * - Custom (registered by users/organizations)
     * 
     * @param intent the inferred intent
     * @param state the current SDLC state
     * @return list of policy violations (empty if no violations)
     */
    List<PolicyViolation> checkPolicies(IntentResult intent, SDLCState state);
    
    /**
     * Registers a custom policy.
     * 
     * Custom policies allow organizations to enforce their own rules
     * (e.g., require code review before production deployment).
     * 
     * @param policy the policy to register
     */
    void registerPolicy(Policy policy);
    
    /**
     * Gets all registered policies.
     * 
     * @return list of all policies (built-in and custom)
     */
    List<Policy> getAllPolicies();
}
