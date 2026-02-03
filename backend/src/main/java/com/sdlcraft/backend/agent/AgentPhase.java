package com.sdlcraft.backend.agent;

/**
 * Phases of agent execution following the PLAN → ACT → OBSERVE → REFLECT pattern.
 * 
 * Each phase represents a distinct stage in the agentic workflow:
 * - PLAN: Determine what needs to be done
 * - ACT: Execute the planned actions
 * - OBSERVE: Validate the results
 * - REFLECT: Learn from the outcome
 */
public enum AgentPhase {
    
    /**
     * Planning phase: Analyze context and create execution plan.
     */
    PLAN,
    
    /**
     * Action phase: Execute the planned steps.
     */
    ACT,
    
    /**
     * Observation phase: Validate execution results.
     */
    OBSERVE,
    
    /**
     * Reflection phase: Analyze outcomes and suggest improvements.
     */
    REFLECT
}
