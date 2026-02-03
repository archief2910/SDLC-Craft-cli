package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;

import java.util.List;

/**
 * Orchestrates multi-agent workflows following the PLAN → ACT → OBSERVE → REFLECT pattern.
 * 
 * The orchestrator coordinates agent execution, manages context flow between agents,
 * handles errors and timeouts, and ensures proper sequencing of agent phases.
 * 
 * Design rationale:
 * - Separates orchestration logic from agent implementation
 * - Enables dynamic agent registration for extensibility
 * - Provides both synchronous and asynchronous execution modes
 * - Maintains execution traces for debugging and learning
 */
public interface AgentOrchestrator {
    
    /**
     * Execute an intent using the appropriate agents.
     * 
     * The orchestrator:
     * 1. Creates initial execution context
     * 2. Selects appropriate agents for the intent
     * 3. Executes agents in PLAN → ACT → OBSERVE → REFLECT order
     * 4. Handles errors and invokes reflection on failures
     * 5. Returns final execution result
     * 
     * @param intent the inferred intent to execute
     * @param state current SDLC state
     * @param userId user executing the intent
     * @param projectId project context
     * @return execution result with outcomes and reasoning
     */
    ExecutionResult execute(IntentResult intent, SDLCState state, String userId, String projectId);
    
    /**
     * Execute an intent asynchronously with progress callbacks.
     * 
     * Allows streaming of execution events back to the CLI for real-time feedback.
     * 
     * @param intent the inferred intent to execute
     * @param state current SDLC state
     * @param userId user executing the intent
     * @param projectId project context
     * @param callback callback for progress events
     * @return execution ID for tracking
     */
    String executeAsync(IntentResult intent, SDLCState state, String userId, String projectId, 
                       ExecutionCallback callback);
    
    /**
     * Register an agent with the orchestrator.
     * 
     * Enables dynamic agent registration at runtime without code changes.
     * Agents are selected based on their type and canHandle() method.
     * 
     * @param agent the agent to register
     */
    void registerAgent(Agent agent);
    
    /**
     * Unregister an agent from the orchestrator.
     * 
     * @param agentType the type of agent to remove
     */
    void unregisterAgent(String agentType);
    
    /**
     * Get all registered agents.
     * 
     * @return list of registered agents
     */
    List<Agent> getRegisteredAgents();
    
    /**
     * Get an agent by type.
     * 
     * @param agentType the agent type to retrieve
     * @return the agent or null if not found
     */
    Agent getAgent(String agentType);
    
    /**
     * Cancel an ongoing execution.
     * 
     * @param executionId the execution to cancel
     * @return true if cancellation was successful
     */
    boolean cancelExecution(String executionId);
    
    /**
     * Get the status of an ongoing execution.
     * 
     * @param executionId the execution to query
     * @return execution status or null if not found
     */
    ExecutionStatus getExecutionStatus(String executionId);
}
