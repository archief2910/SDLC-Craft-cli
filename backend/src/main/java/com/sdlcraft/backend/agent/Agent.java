package com.sdlcraft.backend.agent;

/**
 * Core interface for all agents in the SDLCraft system.
 * 
 * Agents follow the PLAN → ACT → OBSERVE → REFLECT pattern to execute
 * complex SDLC workflows autonomously. Each agent type specializes in
 * a specific phase of execution.
 * 
 * Design rationale:
 * - Separate methods for each phase enable clear separation of concerns
 * - AgentContext carries state across phases without tight coupling
 * - AgentResult provides structured output for orchestration
 * - Interface allows for diverse agent implementations
 */
public interface Agent {
    
    /**
     * Get the unique type identifier for this agent.
     * Used for agent registration and discovery.
     * 
     * @return agent type (e.g., "planner", "executor", "validator", "reflection")
     */
    String getType();
    
    /**
     * PLAN phase: Analyze the context and create an execution plan.
     * 
     * This phase determines WHAT needs to be done and HOW to do it.
     * The planner examines the intent, current state, and available resources
     * to create a concrete execution plan.
     * 
     * @param context execution context with intent, state, and parameters
     * @return result containing the execution plan or error
     */
    AgentResult plan(AgentContext context);
    
    /**
     * ACT phase: Execute the planned actions.
     * 
     * This phase performs the actual work defined in the plan.
     * The executor runs commands, makes API calls, or performs other
     * concrete actions to accomplish the intent.
     * 
     * @param context execution context with plan and parameters
     * @return result containing action outcomes or error
     */
    AgentResult act(AgentContext context);
    
    /**
     * OBSERVE phase: Validate and analyze execution results.
     * 
     * This phase checks whether the actions produced the expected outcomes.
     * The validator examines results against success criteria and identifies
     * any anomalies or failures.
     * 
     * @param context execution context with action results
     * @return result containing validation findings or error
     */
    AgentResult observe(AgentContext context);
    
    /**
     * REFLECT phase: Analyze outcomes and suggest improvements.
     * 
     * This phase learns from execution by comparing expected vs actual results.
     * The reflection agent identifies root causes of failures and suggests
     * recovery actions or plan adjustments.
     * 
     * @param context execution context with observations and history
     * @return result containing analysis and suggestions or error
     */
    AgentResult reflect(AgentContext context);
    
    /**
     * Check if this agent can handle the given context.
     * 
     * Allows agents to declare their capabilities and constraints.
     * Used by the orchestrator to select appropriate agents.
     * 
     * @param context execution context to evaluate
     * @return true if this agent can handle the context
     */
    boolean canHandle(AgentContext context);
}
