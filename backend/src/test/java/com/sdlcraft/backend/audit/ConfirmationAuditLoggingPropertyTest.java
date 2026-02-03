package com.sdlcraft.backend.audit;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.policy.DefaultPolicyEngine;
import com.sdlcraft.backend.policy.PolicyEngine;
import com.sdlcraft.backend.policy.RiskAssessment;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Confirmation Audit Logging.
 * 
 * Feature: sdlcraft-cli
 * Property 25: Confirmation Audit Logging
 * Validates: Requirements 6.4, 5.4
 * 
 * This test verifies that for any confirmed high-risk action, the Backend creates
 * an audit log entry containing the confirmation, timestamp, and user context.
 * 
 * The property ensures:
 * - All confirmation events are logged
 * - Logs contain required fields (userId, projectId, timestamp)
 * - Confirmation status (granted/denied) is recorded
 * - Risk assessment details are preserved
 * - Logs are queryable and retrievable
 */
@DataJpaTest
@SpringJUnitConfig
class ConfirmationAuditLoggingPropertyTest {
    
    private TestEntityManager entityManager;
    private AuditLogRepository auditLogRepository;
    private AuditService auditService;
    private PolicyEngine policyEngine;
    
    @BeforeTry
    void setUp() {
        // Note: In a real Spring test, these would be injected
        // For property tests, we create them manually
        policyEngine = new DefaultPolicyEngine();
    }
    
    /**
     * Property 25: Confirmation required events are logged
     * 
     * For any high-risk command requiring confirmation, calling logConfirmationRequired
     * should create an audit log entry with requiresConfirmation=true.
     */
    @Property(tries = 100)
    @Label("Confirmation required events are logged with correct fields")
    void confirmationRequiredEventsAreLogged(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk (should be high)
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service for testing
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation requirement
        service.logConfirmationRequired(userId, projectId, intentResult, riskAssessment);
        
        // Verify audit log was created
        List<AuditLog> logs = repository.findAll();
        assertThat(logs)
                .as("Confirmation required should create audit log")
                .hasSize(1);
        
        AuditLog log = logs.get(0);
        
        // Verify required fields
        assertThat(log.getUserId())
                .as("Audit log should contain user ID")
                .isEqualTo(userId);
        
        assertThat(log.getProjectId())
                .as("Audit log should contain project ID")
                .isEqualTo(projectId);
        
        assertThat(log.getAction())
                .as("Audit log action should be CONFIRMATION_REQUIRED")
                .isEqualTo(AuditLog.AuditAction.CONFIRMATION_REQUIRED);
        
        assertThat(log.getRequiresConfirmation())
                .as("Audit log should mark requiresConfirmation as true")
                .isTrue();
        
        assertThat(log.getTimestamp())
                .as("Audit log should have timestamp")
                .isNotNull()
                .isBefore(LocalDateTime.now().plusSeconds(1));
        
        // Verify metadata contains intent and risk details
        assertThat(log.getMetadata())
                .as("Audit log should contain metadata")
                .isNotNull()
                .containsKeys("intent", "target", "riskLevel", "concerns", "explanation");
        
        assertThat(log.getMetadata().get("intent"))
                .as("Metadata should contain intent")
                .isEqualTo(intent);
        
        assertThat(log.getMetadata().get("target"))
                .as("Metadata should contain target")
                .isEqualTo(target);
    }
    
    /**
     * Property 25: Confirmation granted events are logged
     * 
     * For any confirmed high-risk action, calling logConfirmationGranted
     * should create an audit log entry with wasConfirmed=true.
     */
    @Property(tries = 100)
    @Label("Confirmation granted events are logged with wasConfirmed=true")
    void confirmationGrantedEventsAreLogged(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation granted
        service.logConfirmationGranted(userId, projectId, intentResult, riskAssessment);
        
        // Verify audit log was created
        List<AuditLog> logs = repository.findAll();
        assertThat(logs)
                .as("Confirmation granted should create audit log")
                .hasSize(1);
        
        AuditLog log = logs.get(0);
        
        // Verify confirmation status
        assertThat(log.getAction())
                .as("Audit log action should be CONFIRMATION_GRANTED")
                .isEqualTo(AuditLog.AuditAction.CONFIRMATION_GRANTED);
        
        assertThat(log.getRequiresConfirmation())
                .as("Audit log should mark requiresConfirmation as true")
                .isTrue();
        
        assertThat(log.getWasConfirmed())
                .as("Audit log should mark wasConfirmed as true")
                .isTrue();
        
        // Verify user context is preserved
        assertThat(log.getUserId())
                .as("User ID should be preserved")
                .isEqualTo(userId);
        
        assertThat(log.getProjectId())
                .as("Project ID should be preserved")
                .isEqualTo(projectId);
        
        // Verify timestamp is recorded
        assertThat(log.getMetadata().get("confirmedAt"))
                .as("Confirmation timestamp should be recorded")
                .isNotNull();
    }
    
    /**
     * Property 25: Confirmation denied events are logged
     * 
     * For any denied high-risk action, calling logConfirmationDenied
     * should create an audit log entry with wasConfirmed=false.
     */
    @Property(tries = 100)
    @Label("Confirmation denied events are logged with wasConfirmed=false")
    void confirmationDeniedEventsAreLogged(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation denied
        service.logConfirmationDenied(userId, projectId, intentResult, riskAssessment);
        
        // Verify audit log was created
        List<AuditLog> logs = repository.findAll();
        assertThat(logs)
                .as("Confirmation denied should create audit log")
                .hasSize(1);
        
        AuditLog log = logs.get(0);
        
        // Verify denial status
        assertThat(log.getAction())
                .as("Audit log action should be CONFIRMATION_DENIED")
                .isEqualTo(AuditLog.AuditAction.CONFIRMATION_DENIED);
        
        assertThat(log.getRequiresConfirmation())
                .as("Audit log should mark requiresConfirmation as true")
                .isTrue();
        
        assertThat(log.getWasConfirmed())
                .as("Audit log should mark wasConfirmed as false")
                .isFalse();
        
        // Verify timestamp is recorded
        assertThat(log.getMetadata().get("deniedAt"))
                .as("Denial timestamp should be recorded")
                .isNotNull();
    }
    
    /**
     * Property 25: Multiple confirmation events are logged independently
     * 
     * For any sequence of confirmation events, each should create a separate
     * audit log entry.
     */
    @Property(tries = 50)
    @Label("Multiple confirmation events are logged independently")
    void multipleConfirmationEventsAreLoggedIndependently(
            @ForAll("highRiskIntents") String intent1,
            @ForAll("highRiskIntents") String intent2,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create two different intents
        IntentResult intentResult1 = createIntentResult(intent1, target);
        IntentResult intentResult2 = createIntentResult(intent2, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risks
        RiskAssessment riskAssessment1 = policyEngine.assessRisk(intentResult1, state);
        RiskAssessment riskAssessment2 = policyEngine.assessRisk(intentResult2, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log multiple confirmation events
        service.logConfirmationRequired(userId, projectId, intentResult1, riskAssessment1);
        service.logConfirmationGranted(userId, projectId, intentResult1, riskAssessment1);
        service.logConfirmationRequired(userId, projectId, intentResult2, riskAssessment2);
        service.logConfirmationDenied(userId, projectId, intentResult2, riskAssessment2);
        
        // Verify all events were logged
        List<AuditLog> logs = repository.findAll();
        assertThat(logs)
                .as("All confirmation events should be logged")
                .hasSize(4);
        
        // Verify different action types
        assertThat(logs)
                .extracting(AuditLog::getAction)
                .contains(
                        AuditLog.AuditAction.CONFIRMATION_REQUIRED,
                        AuditLog.AuditAction.CONFIRMATION_GRANTED,
                        AuditLog.AuditAction.CONFIRMATION_DENIED
                );
    }
    
    /**
     * Property 25: Confirmation logs are queryable
     * 
     * For any confirmation events, they should be retrievable via
     * the confirmation events query.
     */
    @Property(tries = 50)
    @Label("Confirmation logs are queryable via findConfirmationEvents")
    void confirmationLogsAreQueryable(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll boolean wasConfirmed) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation event
        if (wasConfirmed) {
            service.logConfirmationGranted(userId, projectId, intentResult, riskAssessment);
        } else {
            service.logConfirmationDenied(userId, projectId, intentResult, riskAssessment);
        }
        
        // Query confirmation events
        List<AuditLog> confirmationLogs = repository.findConfirmationEvents();
        
        // Verify log is retrievable
        assertThat(confirmationLogs)
                .as("Confirmation events should be queryable")
                .hasSize(1)
                .allMatch(log -> log.getRequiresConfirmation() != null && log.getRequiresConfirmation());
    }
    
    /**
     * Property 25: Denied confirmations are queryable separately
     * 
     * For any denied confirmation, it should be retrievable via
     * the denied confirmations query.
     */
    @Property(tries = 50)
    @Label("Denied confirmations are queryable separately")
    void deniedConfirmationsAreQueryableSeparately(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log both granted and denied confirmations
        service.logConfirmationGranted(userId, projectId, intentResult, riskAssessment);
        service.logConfirmationDenied(userId, projectId, intentResult, riskAssessment);
        
        // Query denied confirmations
        List<AuditLog> deniedLogs = repository.findDeniedConfirmations();
        
        // Verify only denied confirmation is returned
        assertThat(deniedLogs)
                .as("Only denied confirmations should be returned")
                .hasSize(1)
                .allMatch(log -> log.getWasConfirmed() != null && !log.getWasConfirmed());
    }
    
    /**
     * Property 25: Confirmation logs preserve risk level
     * 
     * For any high-risk command confirmation, the audit log should
     * preserve the risk level from the risk assessment.
     */
    @Property(tries = 100)
    @Label("Confirmation logs preserve risk level")
    void confirmationLogsPreserveRiskLevel(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation
        service.logConfirmationGranted(userId, projectId, intentResult, riskAssessment);
        
        // Verify risk level is preserved
        List<AuditLog> logs = repository.findAll();
        AuditLog log = logs.get(0);
        
        assertThat(log.getRiskLevel())
                .as("Audit log should preserve risk level")
                .isNotNull();
        
        // Verify metadata contains risk level string
        assertThat(log.getMetadata().get("riskLevel"))
                .as("Metadata should contain risk level")
                .isEqualTo(riskAssessment.getLevel().toString());
    }
    
    /**
     * Property 25: Confirmation logs include description
     * 
     * For any confirmation event, the audit log should include
     * a human-readable description.
     */
    @Property(tries = 100)
    @Label("Confirmation logs include human-readable description")
    void confirmationLogsIncludeDescription(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create in-memory audit service
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditService service = new AuditService(repository);
        
        // Log confirmation
        service.logConfirmationRequired(userId, projectId, intentResult, riskAssessment);
        
        // Verify description is present
        List<AuditLog> logs = repository.findAll();
        AuditLog log = logs.get(0);
        
        assertThat(log.getDescription())
                .as("Audit log should include description")
                .isNotNull()
                .isNotBlank()
                .contains(intent);
    }
    
    /**
     * Property 25: Audit logging failures don't block operations
     * 
     * For any confirmation event, if audit logging fails, it should not
     * throw an exception (verified by the try-catch in AuditService).
     * This property verifies the service handles errors gracefully.
     */
    @Property(tries = 50)
    @Label("Audit logging failures are handled gracefully")
    void auditLoggingFailuresAreHandledGracefully(
            @ForAll("highRiskIntents") String intent,
            @ForAll("highRiskTargets") String target,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create high-risk intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, 0.8);
        
        // Assess risk
        RiskAssessment riskAssessment = policyEngine.assessRisk(intentResult, state);
        
        // Create failing repository
        FailingAuditLogRepository failingRepository = new FailingAuditLogRepository();
        AuditService service = new AuditService(failingRepository);
        
        // Verify no exception is thrown
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            service.logConfirmationGranted(userId, projectId, intentResult, riskAssessment);
        }).as("Audit logging failures should not throw exceptions")
          .doesNotThrowAnyException();
    }
    
    // Helper methods
    
    private IntentResult createIntentResult(String intent, String target) {
        IntentResult result = new IntentResult(intent, target, 0.9, "Test intent");
        result.setModifiers(new HashMap<>());
        return result;
    }
    
    private SDLCState createSDLCState(String projectId, Phase phase, double testCoverage) {
        SDLCState state = new SDLCState(projectId, phase);
        state.setTestCoverage(testCoverage);
        state.setOpenIssues(5);
        state.setTotalIssues(20);
        state.setLastDeployment(LocalDateTime.now().minusDays(7));
        state.setRiskLevel(RiskLevel.LOW);
        return state;
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<String> highRiskIntents() {
        return Arbitraries.of("release", "deploy", "delete", "reset", "destroy");
    }
    
    @Provide
    Arbitrary<String> highRiskTargets() {
        return Arbitraries.of("production", "staging");
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20);
    }
    
    @Provide
    Arbitrary<String> projectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(30);
    }
    
    // In-memory repository for testing
    
    private static class InMemoryAuditLogRepository implements AuditLogRepository {
        private final java.util.List<AuditLog> logs = new java.util.ArrayList<>();
        
        @Override
        public AuditLog save(AuditLog log) {
            logs.add(log);
            return log;
        }
        
        @Override
        public List<AuditLog> findAll() {
            return new java.util.ArrayList<>(logs);
        }
        
        @Override
        public List<AuditLog> findConfirmationEvents() {
            return logs.stream()
                    .filter(log -> log.getRequiresConfirmation() != null && log.getRequiresConfirmation())
                    .toList();
        }
        
        @Override
        public List<AuditLog> findDeniedConfirmations() {
            return logs.stream()
                    .filter(log -> log.getRequiresConfirmation() != null && 
                                  log.getRequiresConfirmation() &&
                                  log.getWasConfirmed() != null &&
                                  !log.getWasConfirmed())
                    .toList();
        }
        
        // Implement other methods as no-ops for testing
        @Override
        public List<AuditLog> findByUserIdOrderByTimestampDesc(String userId) {
            return logs.stream()
                    .filter(log -> log.getUserId().equals(userId))
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByProjectIdOrderByTimestampDesc(String projectId) {
            return logs.stream()
                    .filter(log -> log.getProjectId().equals(projectId))
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByActionOrderByTimestampDesc(AuditLog.AuditAction action) {
            return logs.stream()
                    .filter(log -> log.getAction() == action)
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByRiskLevelOrderByTimestampDesc(AuditLog.AuditRiskLevel riskLevel) {
            return logs.stream()
                    .filter(log -> log.getRiskLevel() == riskLevel)
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
            return logs.stream()
                    .filter(log -> !log.getTimestamp().isBefore(startTime) && 
                                  !log.getTimestamp().isAfter(endTime))
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByProjectAndTimeRange(String projectId, LocalDateTime startTime, LocalDateTime endTime) {
            return logs.stream()
                    .filter(log -> log.getProjectId().equals(projectId) &&
                                  !log.getTimestamp().isBefore(startTime) && 
                                  !log.getTimestamp().isAfter(endTime))
                    .toList();
        }
        
        @Override
        public List<AuditLog> findHighRiskLogs() {
            return logs.stream()
                    .filter(log -> log.getRiskLevel() == AuditLog.AuditRiskLevel.HIGH ||
                                  log.getRiskLevel() == AuditLog.AuditRiskLevel.CRITICAL)
                    .toList();
        }
        
        @Override
        public long countByProjectIdAndAction(String projectId, AuditLog.AuditAction action) {
            return logs.stream()
                    .filter(log -> log.getProjectId().equals(projectId) && log.getAction() == action)
                    .count();
        }
        
        @Override
        public long countHighRiskEventsByProject(String projectId) {
            return logs.stream()
                    .filter(log -> log.getProjectId().equals(projectId) &&
                                  (log.getRiskLevel() == AuditLog.AuditRiskLevel.HIGH ||
                                   log.getRiskLevel() == AuditLog.AuditRiskLevel.CRITICAL))
                    .count();
        }
        
        @Override
        public void flush() {}
        
        @Override
        @SuppressWarnings("unchecked")
        public <S extends AuditLog> S saveAndFlush(S entity) {
            return (S) save(entity);
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <S extends AuditLog> List<S> saveAllAndFlush(Iterable<S> entities) {
            java.util.List<S> saved = new java.util.ArrayList<>();
            entities.forEach(entity -> saved.add((S) save(entity)));
            return saved;
        }
        
        @Override
        public void deleteAllInBatch(Iterable<AuditLog> entities) {
            entities.forEach(logs::remove);
        }
        
        @Override
        public void deleteAllByIdInBatch(Iterable<String> ids) {
            ids.forEach(id -> logs.removeIf(log -> log.getId().equals(id)));
        }
        
        @Override
        public void deleteAllInBatch() {
            logs.clear();
        }
        
        @Override
        public AuditLog getOne(String id) {
            return logs.stream().filter(log -> log.getId().equals(id)).findFirst().orElse(null);
        }
        
        @Override
        public AuditLog getById(String id) {
            return getOne(id);
        }
        
        @Override
        public AuditLog getReferenceById(String id) {
            return getOne(id);
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <S extends AuditLog> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            return (List<S>) findAll();
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <S extends AuditLog> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) {
            return (List<S>) findAll();
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <S extends AuditLog> List<S> saveAll(Iterable<S> entities) {
            java.util.List<S> saved = new java.util.ArrayList<>();
            entities.forEach(entity -> saved.add((S) save(entity)));
            return saved;
        }
        
        @Override
        public java.util.Optional<AuditLog> findById(String id) {
            return logs.stream().filter(log -> log.getId().equals(id)).findFirst();
        }
        
        @Override
        public boolean existsById(String id) {
            return logs.stream().anyMatch(log -> log.getId().equals(id));
        }
        
        @Override
        public List<AuditLog> findAllById(Iterable<String> ids) {
            java.util.Set<String> idSet = new java.util.HashSet<>();
            ids.forEach(idSet::add);
            return logs.stream().filter(log -> idSet.contains(log.getId())).toList();
        }
        
        @Override
        public long count() {
            return logs.size();
        }
        
        @Override
        public void deleteById(String id) {
            logs.removeIf(log -> log.getId().equals(id));
        }
        
        @Override
        public void delete(AuditLog entity) {
            logs.remove(entity);
        }
        
        @Override
        public void deleteAllById(Iterable<? extends String> ids) {
            ids.forEach(this::deleteById);
        }
        
        @Override
        public void deleteAll(Iterable<? extends AuditLog> entities) {
            entities.forEach(logs::remove);
        }
        
        @Override
        public void deleteAll() {
            logs.clear();
        }
        
        @Override
        public List<AuditLog> findAll(org.springframework.data.domain.Sort sort) {
            return findAll();
        }
        
        @Override
        public org.springframework.data.domain.Page<AuditLog> findAll(org.springframework.data.domain.Pageable pageable) {
            return null;
        }
        
        @Override
        public <S extends AuditLog> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
            return java.util.Optional.empty();
        }
        
        @Override
        public <S extends AuditLog> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
            return null;
        }
        
        @Override
        public <S extends AuditLog> long count(org.springframework.data.domain.Example<S> example) {
            return count();
        }
        
        @Override
        public <S extends AuditLog> boolean exists(org.springframework.data.domain.Example<S> example) {
            return !logs.isEmpty();
        }
        
        @Override
        public <S extends AuditLog, R> R findBy(org.springframework.data.domain.Example<S> example, 
                                                 java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }
    }
    
    // Failing repository for error handling tests
    
    private static class FailingAuditLogRepository extends InMemoryAuditLogRepository {
        @Override
        public AuditLog save(AuditLog log) {
            throw new RuntimeException("Simulated repository failure");
        }
    }
}
