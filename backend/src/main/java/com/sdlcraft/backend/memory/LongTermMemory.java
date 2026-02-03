package com.sdlcraft.backend.memory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Long-term memory interface for storing and retrieving project context and command history.
 * 
 * Provides persistent storage of:
 * - Command execution history with outcomes
 * - Project context for semantic retrieval
 * - Historical patterns for learning
 * 
 * Design rationale:
 * - Separates structured data (PostgreSQL) from semantic search (vector store)
 * - Supports both exact and similarity-based queries
 * - Enables learning from past executions
 * - Provides TTL for data retention policies
 */
public interface LongTermMemory {
    
    /**
     * Store a command execution with its outcome.
     * 
     * @param execution the command execution to store
     */
    void storeCommand(CommandExecution execution);
    
    /**
     * Query command executions by intent.
     * 
     * @param intent the intent to search for
     * @param limit maximum number of results
     * @return list of matching executions, most recent first
     */
    List<CommandExecution> queryByIntent(String intent, int limit);
    
    /**
     * Query command executions by time range.
     * 
     * @param startTime start of time range
     * @param endTime end of time range
     * @param limit maximum number of results
     * @return list of matching executions, most recent first
     */
    List<CommandExecution> queryByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int limit);
    
    /**
     * Query command executions by user.
     * 
     * @param userId the user ID to search for
     * @param limit maximum number of results
     * @return list of matching executions, most recent first
     */
    List<CommandExecution> queryByUser(String userId, int limit);
    
    /**
     * Query command executions by project.
     * 
     * @param projectId the project ID to search for
     * @param limit maximum number of results
     * @return list of matching executions, most recent first
     */
    List<CommandExecution> queryByProject(String projectId, int limit);
    
    /**
     * Query similar command executions using semantic search.
     * 
     * Uses vector similarity to find commands with similar intent or context.
     * 
     * @param query the query text (natural language or command)
     * @param limit maximum number of results
     * @return list of similar executions, most similar first
     */
    List<CommandExecution> querySimilar(String query, int limit);
    
    /**
     * Store project context for semantic retrieval.
     * 
     * Context includes project metadata, decisions, patterns, and other
     * information that helps with intent inference and planning.
     * 
     * @param projectId the project ID
     * @param context the context data to store
     */
    void storeContext(String projectId, Map<String, Object> context);
    
    /**
     * Retrieve project context.
     * 
     * @param projectId the project ID
     * @return the stored context or empty map if not found
     */
    Map<String, Object> retrieveContext(String projectId);
    
    /**
     * Query context using semantic similarity.
     * 
     * @param projectId the project ID
     * @param query the query text
     * @param limit maximum number of results
     * @return list of relevant context entries
     */
    List<ContextEntry> queryContextSimilar(String projectId, String query, int limit);
    
    /**
     * Delete old command executions based on TTL.
     * 
     * @param olderThan delete executions older than this date
     * @return number of executions deleted
     */
    int deleteOldExecutions(LocalDateTime olderThan);
    
    /**
     * Get execution statistics for a project.
     * 
     * @param projectId the project ID
     * @return statistics about command executions
     */
    ExecutionStatistics getStatistics(String projectId);
}
