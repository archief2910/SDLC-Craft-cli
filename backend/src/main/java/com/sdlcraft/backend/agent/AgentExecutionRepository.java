package com.sdlcraft.backend.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for agent execution persistence.
 * 
 * Provides queries for execution history, pattern analysis, and debugging.
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, String> {
    
    /**
     * Find executions by project ID.
     */
    List<AgentExecutionEntity> findByProjectIdOrderByStartTimeDesc(String projectId);
    
    /**
     * Find executions by user ID.
     */
    List<AgentExecutionEntity> findByUserIdOrderByStartTimeDesc(String userId);
    
    /**
     * Find executions by intent.
     */
    List<AgentExecutionEntity> findByIntentOrderByStartTimeDesc(String intent);
    
    /**
     * Find executions by status.
     */
    List<AgentExecutionEntity> findByOverallStatusOrderByStartTimeDesc(AgentStatus status);
    
    /**
     * Find executions within time range.
     */
    @Query("SELECT e FROM AgentExecutionEntity e WHERE e.startTime >= :startTime AND e.startTime <= :endTime ORDER BY e.startTime DESC")
    List<AgentExecutionEntity> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find recent executions for a project.
     */
    @Query("SELECT e FROM AgentExecutionEntity e WHERE e.projectId = :projectId ORDER BY e.startTime DESC")
    List<AgentExecutionEntity> findRecentByProject(@Param("projectId") String projectId);
    
    /**
     * Find failed executions for analysis.
     */
    @Query("SELECT e FROM AgentExecutionEntity e WHERE e.overallStatus = 'FAILURE' ORDER BY e.startTime DESC")
    List<AgentExecutionEntity> findFailedExecutions();
}
