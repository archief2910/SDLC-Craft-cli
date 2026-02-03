package com.sdlcraft.backend.agent;

/**
 * Callback interface for asynchronous execution progress events.
 * 
 * Enables streaming of execution events back to the CLI for real-time feedback.
 * Implementations can send events via SSE, WebSocket, or other streaming mechanisms.
 */
public interface ExecutionCallback {
    
    /**
     * Called when an agent phase starts.
     * 
     * @param agentType the type of agent starting
     * @param phase the phase being executed
     */
    void onPhaseStart(String agentType, AgentPhase phase);
    
    /**
     * Called when an agent phase completes.
     * 
     * @param result the result of the phase
     */
    void onPhaseComplete(AgentResult result);
    
    /**
     * Called when execution progress is updated.
     * 
     * @param message progress message
     * @param percentComplete percentage complete (0-100)
     */
    void onProgress(String message, int percentComplete);
    
    /**
     * Called when execution completes (success or failure).
     * 
     * @param result final execution result
     */
    void onComplete(ExecutionResult result);
    
    /**
     * Called when an error occurs during execution.
     * 
     * @param error error message
     * @param exception optional exception
     */
    void onError(String error, Throwable exception);
}
