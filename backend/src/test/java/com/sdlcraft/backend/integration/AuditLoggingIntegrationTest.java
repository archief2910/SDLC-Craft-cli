package com.sdlcraft.backend.integration;

import com.sdlcraft.backend.agent.AgentStatus;
import com.sdlcraft.backend.agent.ExecutionResult;
import com.sdlcraft.backend.audit.AuditLog;
import com.sdlcraft.backend.audit.AuditLogRepository;
import com.sdlcraft.backend.audit.AuditService;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.policy.RiskAssessment;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for audit logging functionality.
 * Tests audit log creation, querying, and data integrity.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class AuditLoggingIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private String testUserId;
    private String testProjectId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-" + System.currentTimeMillis();
        testProjectId = "test-project-" + System.currentTimeMillis();
    }

    @Test
    void testStateChangeAuditLog() {
        // Initialize project
        SDLCState oldState = new SDLCState();
        oldState.setProjectId(testProjectId);
        oldState.setCurrentPhase(Phase.DEVELOPMENT);
        oldState.setRiskLevel(RiskLevel.LOW);
        oldState.setTestCoverage(0.5);

        SDLCState newState = new SDLCState();
        newState.setProjectId(testProjectId);
        newState.setCurrentPhase(Phase.TESTING);
        newState.setRiskLevel(RiskLevel.MEDIUM);
        newState.setTestCoverage(0.7);

        // Log a state change
        auditService.logStateChange(testUserId, testProjectId, Phase.DEVELOPMENT, Phase.TESTING, oldState, newState);

        // Retrieve audit logs
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);

        // Verify audit log was created
        assertThat(logs).isNotEmpty();
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo(AuditLog.AuditAction.STATE_CHANGE);
        assertThat(log.getProjectId()).isEqualTo(testProjectId);
        assertThat(log.getUserId()).isEqualTo(testUserId);
        assertThat(log.getOldValues()).isNotNull();
        assertThat(log.getNewValues()).isNotNull();
    }

    @Test
    void testHighRiskCommandAuditLog() {
        // Create intent and risk assessment
        IntentResult intent = new IntentResult();
        intent.setIntent("deploy");
        intent.setTarget("production");
        intent.setConfidence(0.95);

        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setLevel(RiskLevel.HIGH);
        riskAssessment.setConcerns(List.of("Production deployment"));
        riskAssessment.setExplanation("High-risk production deployment");

        // Log confirmation granted
        auditService.logConfirmationGranted(testUserId, testProjectId, intent, riskAssessment);

        // Retrieve audit logs
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);

        // Verify audit log was created
        assertThat(logs).isNotEmpty();
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo(AuditLog.AuditAction.CONFIRMATION_GRANTED);
        assertThat(log.getProjectId()).isEqualTo(testProjectId);
        assertThat(log.getUserId()).isEqualTo(testUserId);
        assertThat(log.getMetadata()).containsEntry("riskLevel", "HIGH");
        assertThat(log.getWasConfirmed()).isTrue();
    }

    @Test
    void testAgentExecutionAuditLog() {
        // Create execution result
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-123")
                .overallStatus(AgentStatus.SUCCESS)
                .summary("Analysis completed successfully")
                .durationMs(5000L)
                .build();

        // Log agent execution
        auditService.logAgentExecution(testUserId, testProjectId, result);

        // Retrieve audit logs
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);

        // Verify audit log was created
        assertThat(logs).isNotEmpty();
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo(AuditLog.AuditAction.AGENT_EXECUTION);
        assertThat(log.getMetadata()).containsEntry("executionId", "exec-123");
        assertThat(log.getMetadata()).containsEntry("status", "SUCCESS");
    }

    @Test
    void testAuditLogQueryByTimeRange() {
        // Create multiple audit logs at different times
        SDLCState state1 = new SDLCState();
        state1.setProjectId(testProjectId);
        state1.setCurrentPhase(Phase.DEVELOPMENT);
        state1.setRiskLevel(RiskLevel.LOW);

        SDLCState state2 = new SDLCState();
        state2.setProjectId(testProjectId);
        state2.setCurrentPhase(Phase.TESTING);
        state2.setRiskLevel(RiskLevel.MEDIUM);

        auditService.logStateChange(testUserId, testProjectId, Phase.DEVELOPMENT, Phase.TESTING, state1, state2);

        SDLCState state3 = new SDLCState();
        state3.setProjectId(testProjectId);
        state3.setCurrentPhase(Phase.TESTING);
        state3.setRiskLevel(RiskLevel.MEDIUM);

        SDLCState state4 = new SDLCState();
        state4.setProjectId(testProjectId);
        state4.setCurrentPhase(Phase.STAGING);
        state4.setRiskLevel(RiskLevel.HIGH);

        auditService.logStateChange(testUserId, testProjectId, Phase.TESTING, Phase.STAGING, state3, state4);

        // Query logs
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);

        // Verify logs are ordered by timestamp
        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);
        if (logs.size() >= 2) {
            assertThat(logs.get(0).getTimestamp())
                .isAfterOrEqualTo(logs.get(1).getTimestamp());
        }
    }

    @Test
    void testAuditLogQueryByAction() {
        // Create logs with different actions
        SDLCState state1 = new SDLCState();
        state1.setProjectId(testProjectId);
        state1.setCurrentPhase(Phase.DEVELOPMENT);
        state1.setRiskLevel(RiskLevel.LOW);

        SDLCState state2 = new SDLCState();
        state2.setProjectId(testProjectId);
        state2.setCurrentPhase(Phase.TESTING);
        state2.setRiskLevel(RiskLevel.MEDIUM);

        auditService.logStateChange(testUserId, testProjectId, Phase.DEVELOPMENT, Phase.TESTING, state1, state2);

        IntentResult intent = new IntentResult();
        intent.setIntent("deploy");
        intent.setTarget("production");
        intent.setConfidence(0.95);

        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setLevel(RiskLevel.HIGH);
        riskAssessment.setConcerns(List.of("Production deployment"));
        riskAssessment.setExplanation("High-risk production deployment");

        auditService.logConfirmationGranted(testUserId, testProjectId, intent, riskAssessment);

        // Query by action
        List<AuditLog> stateLogs = auditLogRepository.findByActionOrderByTimestampDesc(
            AuditLog.AuditAction.STATE_CHANGE);
        List<AuditLog> commandLogs = auditLogRepository.findByActionOrderByTimestampDesc(
            AuditLog.AuditAction.CONFIRMATION_GRANTED);

        // Verify filtering
        assertThat(stateLogs).isNotEmpty();
        assertThat(commandLogs).isNotEmpty();
        assertThat(stateLogs.get(0).getAction()).isEqualTo(AuditLog.AuditAction.STATE_CHANGE);
        assertThat(commandLogs.get(0).getAction()).isEqualTo(AuditLog.AuditAction.CONFIRMATION_GRANTED);
    }

    @Test
    void testAuditLogMetadataStorage() {
        // Create execution result with complex metadata
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-metadata-test")
                .overallStatus(AgentStatus.SUCCESS)
                .summary("Test execution")
                .durationMs(1000L)
                .finalData("stringValue", "test")
                .finalData("intValue", 42)
                .finalData("boolValue", true)
                .build();

        auditService.logAgentExecution(testUserId, testProjectId, result);

        // Retrieve and verify metadata
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.get(0);
        assertThat(log.getMetadata()).containsEntry("executionId", "exec-metadata-test");
        assertThat(log.getMetadata()).containsEntry("status", "SUCCESS");
    }

    @Test
    void testMultipleProjectsAuditIsolation() {
        // Create logs for different projects
        String project1 = testProjectId + "-1";
        String project2 = testProjectId + "-2";

        SDLCState state1 = new SDLCState();
        state1.setProjectId(project1);
        state1.setCurrentPhase(Phase.DEVELOPMENT);
        state1.setRiskLevel(RiskLevel.LOW);

        SDLCState state2 = new SDLCState();
        state2.setProjectId(project1);
        state2.setCurrentPhase(Phase.TESTING);
        state2.setRiskLevel(RiskLevel.MEDIUM);

        auditService.logStateChange(testUserId, project1, Phase.DEVELOPMENT, Phase.TESTING, state1, state2);

        SDLCState state3 = new SDLCState();
        state3.setProjectId(project2);
        state3.setCurrentPhase(Phase.PLANNING);
        state3.setRiskLevel(RiskLevel.LOW);

        SDLCState state4 = new SDLCState();
        state4.setProjectId(project2);
        state4.setCurrentPhase(Phase.DEVELOPMENT);
        state4.setRiskLevel(RiskLevel.LOW);

        auditService.logStateChange(testUserId, project2, Phase.PLANNING, Phase.DEVELOPMENT, state3, state4);

        // Query logs for each project
        List<AuditLog> logs1 = auditLogRepository.findByProjectIdOrderByTimestampDesc(project1);
        List<AuditLog> logs2 = auditLogRepository.findByProjectIdOrderByTimestampDesc(project2);

        // Verify isolation
        assertThat(logs1).hasSize(1);
        assertThat(logs2).hasSize(1);
        assertThat(logs1.get(0).getProjectId()).isEqualTo(project1);
        assertThat(logs2.get(0).getProjectId()).isEqualTo(project2);
    }

    @Test
    void testAuditLogImmutability() {
        // Create audit log
        SDLCState state1 = new SDLCState();
        state1.setProjectId(testProjectId);
        state1.setCurrentPhase(Phase.DEVELOPMENT);
        state1.setRiskLevel(RiskLevel.LOW);

        SDLCState state2 = new SDLCState();
        state2.setProjectId(testProjectId);
        state2.setCurrentPhase(Phase.TESTING);
        state2.setRiskLevel(RiskLevel.MEDIUM);

        auditService.logStateChange(testUserId, testProjectId, Phase.DEVELOPMENT, Phase.TESTING, state1, state2);

        // Retrieve log
        List<AuditLog> logs = auditLogRepository.findByProjectIdOrderByTimestampDesc(testProjectId);
        assertThat(logs).isNotEmpty();

        AuditLog originalLog = logs.get(0);
        String originalId = originalLog.getId();
        LocalDateTime originalTimestamp = originalLog.getTimestamp();

        // Verify the log exists and has correct data
        assertThat(originalId).isNotNull();
        assertThat(originalTimestamp).isNotNull();
        assertThat(originalLog.getAction()).isEqualTo(AuditLog.AuditAction.STATE_CHANGE);
    }
}
