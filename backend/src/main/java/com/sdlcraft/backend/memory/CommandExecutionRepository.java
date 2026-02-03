package com.sdlcraft.backend.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for command execution persistence.
 */
@Repository
public interface CommandExecutionRepository extends JpaRepository<CommandExecution, String> {
    
    /**
     * Find executions by intent.
     */
    List<CommandExecution> findByIntentOrderByStartedAtDesc(String intent);
    
    /**
     * Find executions by intent with limit.
     */
    @Query("SELECT e FROM CommandExecution e WHERE e.intent = :intent ORDER BY e.startedAt DESC")
    List<CommandExecution> findByIntentWithLimit(@Param("intent") String intent, 
                                                 Pageable pageable);
    
    /**
     * Find executions by user.
     */
    @Query("SELECT e FROM CommandExecution e WHERE e.userId = :userId ORDER BY e.startedAt DESC")
    List<CommandExecution> findByUserWithLimit(@Param("userId") String userId, 
                                               Pageable pageable);
    
    /**
     * Find executions by project.
     */
    @Query("SELECT e FROM CommandExecution e WHERE e.projectId = :projectId ORDER BY e.startedAt DESC")
    List<CommandExecution> findByProjectWithLimit(@Param("projectId") String projectId, 
                                                  Pageable pageable);
    
    /**
     * Find executions within time range.
     */
    @Query("SELECT e FROM CommandExecution e WHERE e.startedAt >= :startTime AND e.startedAt <= :endTime ORDER BY e.startedAt DESC")
    List<CommandExecution> findByTimeRangeWithLimit(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    Pageable pageable);
    
    /**
     * Delete executions older than specified date.
     */
    @Modifying
    @Query("DELETE FROM CommandExecution e WHERE e.startedAt < :olderThan")
    int deleteOlderThan(@Param("olderThan") LocalDateTime olderThan);
    
    /**
     * Count executions by project.
     */
    long countByProjectId(String projectId);
    
    /**
     * Count successful executions by project.
     */
    long countByProjectIdAndStatus(String projectId, CommandExecution.ExecutionStatus status);
}
