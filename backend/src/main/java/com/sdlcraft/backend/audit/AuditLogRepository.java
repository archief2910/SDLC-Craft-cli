package com.sdlcraft.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit log persistence and querying.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    /**
     * Find audit logs by user ID.
     */
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
    
    /**
     * Find audit logs by project ID.
     */
    List<AuditLog> findByProjectIdOrderByTimestampDesc(String projectId);
    
    /**
     * Find audit logs by action type.
     */
    List<AuditLog> findByActionOrderByTimestampDesc(AuditLog.AuditAction action);
    
    /**
     * Find audit logs by risk level.
     */
    List<AuditLog> findByRiskLevelOrderByTimestampDesc(AuditLog.AuditRiskLevel riskLevel);
    
    /**
     * Find audit logs within time range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :startTime AND a.timestamp <= :endTime ORDER BY a.timestamp DESC")
    List<AuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find audit logs by project and time range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.projectId = :projectId AND a.timestamp >= :startTime AND a.timestamp <= :endTime ORDER BY a.timestamp DESC")
    List<AuditLog> findByProjectAndTimeRange(@Param("projectId") String projectId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find high-risk audit logs.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.riskLevel IN ('HIGH', 'CRITICAL') ORDER BY a.timestamp DESC")
    List<AuditLog> findHighRiskLogs();
    
    /**
     * Find confirmation events.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.requiresConfirmation = true ORDER BY a.timestamp DESC")
    List<AuditLog> findConfirmationEvents();
    
    /**
     * Find denied confirmations.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.requiresConfirmation = true AND a.wasConfirmed = false ORDER BY a.timestamp DESC")
    List<AuditLog> findDeniedConfirmations();
    
    /**
     * Count audit logs by action for a project.
     */
    long countByProjectIdAndAction(String projectId, AuditLog.AuditAction action);
    
    /**
     * Count high-risk events for a project.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.projectId = :projectId AND a.riskLevel IN ('HIGH', 'CRITICAL')")
    long countHighRiskEventsByProject(@Param("projectId") String projectId);
}
