package com.sdlcraft.backend.audit;

import com.sdlcraft.backend.agent.ExecutionResult;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.policy.RiskAssessment;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for audit logging of system events.
 * 
 * Emits audit events for:
 * - State changes
 * - High-risk command confirmations
 * - Agent executions
 * - Policy violations
 * - Intent inferences
 * 
 * Design rationale:
 * - Centralized audit logging
 * - Async event emission (doesn't block operations)
 * - Structured audit trail
 * - Query capabilities for compliance
 */
@Service
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    private final AuditLogRepository auditLogRepository;
    
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    /**
     * Log SDLC state change.
     */
    @Transactional
    public void logStateChange(String userId, String projectId, Phase oldPhase, Phase newPhase,
                               SDLCState oldState, SDLCState newState) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.STATE_CHANGE);
            log.setEntityType("SDLCState");
            log.setEntityId(projectId);
            
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("phase", oldPhase.toString());
            if (oldState != null) {
                oldValues.put("riskLevel", oldState.getRiskLevel().toString());
                oldValues.put("testCoverage", oldState.getTestCoverage());
            }
            log.setOldValues(oldValues);
            
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("phase", newPhase.toString());
            if (newState != null) {
                newValues.put("riskLevel", newState.getRiskLevel().toString());
                newValues.put("testCoverage", newState.getTestCoverage());
            }
            log.setNewValues(newValues);
            
            log.setDescription("SDLC phase transitioned from " + oldPhase + " to " + newPhase);
            log.setRiskLevel(determineRiskLevel(newPhase));
            
            auditLogRepository.save(log);
            logger.debug("Logged state change: {} -> {}", oldPhase, newPhase);
            
        } catch (Exception e) {
            // Don't fail operations if audit logging fails
            logger.error("Failed to log state change", e);
        }
    }
    
    /**
     * Log command execution.
     */
    @Transactional
    public void logCommandExecution(String userId, String projectId, String rawCommand,
                                   IntentResult intent, ExecutionResult result) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.COMMAND_EXECUTED);
            log.setEntityType("Command");
            log.setEntityId(result.getExecutionId());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rawCommand", rawCommand);
            metadata.put("intent", intent.getIntent());
            metadata.put("target", intent.getTarget());
            metadata.put("status", result.getOverallStatus().toString());
            metadata.put("durationMs", result.getDurationMs());
            log.setMetadata(metadata);
            
            log.setDescription("Executed command: " + rawCommand);
            log.setRiskLevel(AuditLog.AuditRiskLevel.LOW);
            
            auditLogRepository.save(log);
            logger.debug("Logged command execution: {}", rawCommand);
            
        } catch (Exception e) {
            logger.error("Failed to log command execution", e);
        }
    }
    
    /**
     * Log confirmation requirement.
     */
    @Transactional
    public void logConfirmationRequired(String userId, String projectId, IntentResult intent,
                                       RiskAssessment riskAssessment) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.CONFIRMATION_REQUIRED);
            log.setEntityType("Intent");
            log.setEntityId(intent.getIntent());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("intent", intent.getIntent());
            metadata.put("target", intent.getTarget());
            metadata.put("riskLevel", riskAssessment.getLevel().toString());
            metadata.put("concerns", riskAssessment.getConcerns());
            metadata.put("explanation", riskAssessment.getExplanation());
            log.setMetadata(metadata);
            
            log.setDescription("High-risk command requires confirmation: " + intent.getIntent() + 
                             " " + intent.getTarget());
            log.setRiskLevel(mapRiskLevel(riskAssessment.getLevel()));
            log.setRequiresConfirmation(true);
            
            auditLogRepository.save(log);
            logger.debug("Logged confirmation requirement for: {}", intent.getIntent());
            
        } catch (Exception e) {
            logger.error("Failed to log confirmation requirement", e);
        }
    }
    
    /**
     * Log confirmation granted.
     */
    @Transactional
    public void logConfirmationGranted(String userId, String projectId, IntentResult intent,
                                      RiskAssessment riskAssessment) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.CONFIRMATION_GRANTED);
            log.setEntityType("Intent");
            log.setEntityId(intent.getIntent());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("intent", intent.getIntent());
            metadata.put("target", intent.getTarget());
            metadata.put("riskLevel", riskAssessment.getLevel().toString());
            metadata.put("confirmedAt", LocalDateTime.now().toString());
            log.setMetadata(metadata);
            
            log.setDescription("User confirmed high-risk command: " + intent.getIntent() + 
                             " " + intent.getTarget());
            log.setRiskLevel(mapRiskLevel(riskAssessment.getLevel()));
            log.setRequiresConfirmation(true);
            log.setWasConfirmed(true);
            
            auditLogRepository.save(log);
            logger.info("Logged confirmation granted for: {}", intent.getIntent());
            
        } catch (Exception e) {
            logger.error("Failed to log confirmation granted", e);
        }
    }
    
    /**
     * Log confirmation denied.
     */
    @Transactional
    public void logConfirmationDenied(String userId, String projectId, IntentResult intent,
                                     RiskAssessment riskAssessment) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.CONFIRMATION_DENIED);
            log.setEntityType("Intent");
            log.setEntityId(intent.getIntent());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("intent", intent.getIntent());
            metadata.put("target", intent.getTarget());
            metadata.put("riskLevel", riskAssessment.getLevel().toString());
            metadata.put("deniedAt", LocalDateTime.now().toString());
            log.setMetadata(metadata);
            
            log.setDescription("User denied high-risk command: " + intent.getIntent() + 
                             " " + intent.getTarget());
            log.setRiskLevel(mapRiskLevel(riskAssessment.getLevel()));
            log.setRequiresConfirmation(true);
            log.setWasConfirmed(false);
            
            auditLogRepository.save(log);
            logger.info("Logged confirmation denied for: {}", intent.getIntent());
            
        } catch (Exception e) {
            logger.error("Failed to log confirmation denied", e);
        }
    }
    
    /**
     * Log agent execution.
     */
    @Transactional
    public void logAgentExecution(String userId, String projectId, ExecutionResult result) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.AGENT_EXECUTION);
            log.setEntityType("AgentExecution");
            log.setEntityId(result.getExecutionId());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("executionId", result.getExecutionId());
            metadata.put("status", result.getOverallStatus().toString());
            metadata.put("summary", result.getSummary());
            metadata.put("durationMs", result.getDurationMs());
            metadata.put("agentCount", result.getAgentResults().size());
            log.setMetadata(metadata);
            
            log.setDescription("Agent execution completed: " + result.getSummary());
            log.setRiskLevel(AuditLog.AuditRiskLevel.LOW);
            
            auditLogRepository.save(log);
            logger.debug("Logged agent execution: {}", result.getExecutionId());
            
        } catch (Exception e) {
            logger.error("Failed to log agent execution", e);
        }
    }
    
    /**
     * Log policy violation.
     */
    @Transactional
    public void logPolicyViolation(String userId, String projectId, String policyName,
                                  String violation, String severity) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setProjectId(projectId);
            log.setAction(AuditLog.AuditAction.POLICY_VIOLATION);
            log.setEntityType("Policy");
            log.setEntityId(policyName);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("policyName", policyName);
            metadata.put("violation", violation);
            metadata.put("severity", severity);
            log.setMetadata(metadata);
            
            log.setDescription("Policy violation: " + policyName + " - " + violation);
            log.setRiskLevel("ERROR".equals(severity) ? AuditLog.AuditRiskLevel.HIGH : 
                           AuditLog.AuditRiskLevel.MEDIUM);
            
            auditLogRepository.save(log);
            logger.warn("Logged policy violation: {}", policyName);
            
        } catch (Exception e) {
            logger.error("Failed to log policy violation", e);
        }
    }
    
    /**
     * Query audit logs by project.
     */
    public List<AuditLog> queryByProject(String projectId) {
        return auditLogRepository.findByProjectIdOrderByTimestampDesc(projectId);
    }
    
    /**
     * Query audit logs by time range.
     */
    public List<AuditLog> queryByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogRepository.findByTimeRange(startTime, endTime);
    }
    
    /**
     * Query high-risk audit logs.
     */
    public List<AuditLog> queryHighRiskLogs() {
        return auditLogRepository.findHighRiskLogs();
    }
    
    /**
     * Query confirmation events.
     */
    public List<AuditLog> queryConfirmationEvents() {
        return auditLogRepository.findConfirmationEvents();
    }
    
    /**
     * Determine risk level from SDLC phase.
     */
    private AuditLog.AuditRiskLevel determineRiskLevel(Phase phase) {
        switch (phase) {
            case PRODUCTION:
                return AuditLog.AuditRiskLevel.CRITICAL;
            case STAGING:
                return AuditLog.AuditRiskLevel.HIGH;
            case TESTING:
                return AuditLog.AuditRiskLevel.MEDIUM;
            default:
                return AuditLog.AuditRiskLevel.LOW;
        }
    }
    
    /**
     * Map policy risk level to audit risk level.
     */
    private AuditLog.AuditRiskLevel mapRiskLevel(com.sdlcraft.backend.sdlc.RiskLevel riskLevel) {
        switch (riskLevel) {
            case CRITICAL:
                return AuditLog.AuditRiskLevel.CRITICAL;
            case HIGH:
                return AuditLog.AuditRiskLevel.HIGH;
            case MEDIUM:
                return AuditLog.AuditRiskLevel.MEDIUM;
            case LOW:
            default:
                return AuditLog.AuditRiskLevel.LOW;
        }
    }
}
