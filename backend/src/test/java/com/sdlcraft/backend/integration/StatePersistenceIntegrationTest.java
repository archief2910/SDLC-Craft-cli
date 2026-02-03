package com.sdlcraft.backend.integration;

import com.sdlcraft.backend.sdlc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for SDLC state persistence and retrieval.
 * Tests database operations, transactions, and state consistency.
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
class StatePersistenceIntegrationTest {

    @Autowired
    private SDLCStateMachine stateMachine;

    @Autowired
    private SDLCStateRepository stateRepository;

    private String testProjectId;

    @BeforeEach
    void setUp() {
        testProjectId = "test-project-" + System.currentTimeMillis();
    }

    @Test
    void testStateCreationAndRetrieval() {
        // Initialize project state
        SDLCState state = stateMachine.initializeProject(testProjectId);

        // Verify state was created correctly
        assertThat(state).isNotNull();
        assertThat(state.getProjectId()).isEqualTo(testProjectId);
        assertThat(state.getCurrentPhase()).isEqualTo(Phase.PLANNING);
        assertThat(state.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(state.getTestCoverage()).isEqualTo(0.0);

        // Retrieve state
        SDLCState retrieved = stateMachine.getCurrentState(testProjectId);

        // Verify state was persisted correctly
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getProjectId()).isEqualTo(testProjectId);
        assertThat(retrieved.getCurrentPhase()).isEqualTo(Phase.PLANNING);
        assertThat(retrieved.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(retrieved.getTestCoverage()).isEqualTo(0.0);
    }

    @Test
    void testPhaseTransition() {
        // Initialize project in PLANNING
        stateMachine.initializeProject(testProjectId);

        // Transition to DEVELOPMENT
        stateMachine.transitionTo(testProjectId, Phase.DEVELOPMENT);

        // Verify transition
        SDLCState updated = stateMachine.getCurrentState(testProjectId);
        assertThat(updated.getCurrentPhase()).isEqualTo(Phase.DEVELOPMENT);
    }

    @Test
    void testMetricsUpdate() {
        // Initialize project
        stateMachine.initializeProject(testProjectId);

        // Update metrics
        Metrics metrics = new Metrics();
        metrics.setTestCoverage(0.75);
        metrics.setOpenIssues(5);
        metrics.setTotalIssues(20);
        
        stateMachine.updateMetrics(testProjectId, metrics);

        // Verify metrics were updated
        SDLCState updated = stateMachine.getCurrentState(testProjectId);
        assertThat(updated.getTestCoverage()).isEqualTo(0.75);
        assertThat(updated.getOpenIssues()).isEqualTo(5);
    }

    @Test
    void testCustomMetricsPersistence() {
        // Initialize project
        stateMachine.initializeProject(testProjectId);
        
        // Add custom metrics
        stateMachine.addCustomMetric(testProjectId, "deploymentFrequency", 5);
        stateMachine.addCustomMetric(testProjectId, "meanTimeToRecovery", 2.5);
        stateMachine.addCustomMetric(testProjectId, "changeFailureRate", 0.1);

        // Retrieve and verify custom metrics
        Map<String, Object> customMetrics = stateMachine.getCustomMetrics(testProjectId);
        assertThat(customMetrics).isNotNull();
        assertThat(customMetrics).containsEntry("deploymentFrequency", 5);
        assertThat(customMetrics).containsEntry("meanTimeToRecovery", 2.5);
        assertThat(customMetrics).containsEntry("changeFailureRate", 0.1);
    }

    @Test
    void testReleaseReadinessCalculation() {
        // Initialize project with metrics
        stateMachine.initializeProject(testProjectId);
        
        Metrics metrics = new Metrics();
        metrics.setTestCoverage(0.85);
        metrics.setOpenIssues(3);
        metrics.setTotalIssues(20);
        metrics.setLastDeployment(LocalDateTime.now().minusDays(7));
        stateMachine.updateMetrics(testProjectId, metrics);

        // Calculate release readiness
        ReleaseReadiness readiness = stateMachine.calculateReadiness(testProjectId);

        // Verify readiness calculation
        assertThat(readiness).isNotNull();
        assertThat(readiness.getScore()).isBetween(0.0, 1.0);
        assertThat(readiness.getStatus()).isNotNull();
        assertThat(readiness.getReadyFactors()).isNotNull();
        assertThat(readiness.getBlockingFactors()).isNotNull();
    }

    @Test
    void testConcurrentStateUpdates() throws InterruptedException {
        // Initialize project
        stateMachine.initializeProject(testProjectId);

        // Update metrics concurrently
        Thread thread1 = new Thread(() -> {
            Metrics metrics = new Metrics();
            metrics.setTestCoverage(0.6);
            stateMachine.updateMetrics(testProjectId, metrics);
        });

        Thread thread2 = new Thread(() -> {
            Metrics metrics = new Metrics();
            metrics.setOpenIssues(8);
            stateMachine.updateMetrics(testProjectId, metrics);
        });

        thread1.start();
        thread2.start();

        thread1.join(5000);
        thread2.join(5000);

        // Verify final state is consistent
        SDLCState finalState = stateMachine.getCurrentState(testProjectId);
        assertThat(finalState).isNotNull();
        assertThat(finalState.getProjectId()).isEqualTo(testProjectId);
    }

    @Test
    void testStateTimestampUpdates() throws InterruptedException {
        // Initialize project
        stateMachine.initializeProject(testProjectId);

        LocalDateTime firstUpdate = stateMachine.getCurrentState(testProjectId).getUpdatedAt();

        // Wait a bit
        Thread.sleep(100);

        // Update state
        Metrics metrics = new Metrics();
        metrics.setTestCoverage(0.8);
        stateMachine.updateMetrics(testProjectId, metrics);

        LocalDateTime secondUpdate = stateMachine.getCurrentState(testProjectId).getUpdatedAt();

        // Verify timestamp was updated
        assertThat(secondUpdate).isAfter(firstUpdate);
    }

    @Test
    void testMultipleProjectsIsolation() {
        // Initialize states for multiple projects
        String project1 = testProjectId + "-1";
        String project2 = testProjectId + "-2";

        stateMachine.initializeProject(project1);
        stateMachine.initializeProject(project2);

        // Update project 1 - transition to DEVELOPMENT
        stateMachine.transitionTo(project1, Phase.DEVELOPMENT);
        Metrics metrics1 = new Metrics();
        metrics1.setTestCoverage(0.5);
        stateMachine.updateMetrics(project1, metrics1);

        // Update project 2 - transition to DEVELOPMENT then TESTING
        stateMachine.transitionTo(project2, Phase.DEVELOPMENT);
        stateMachine.transitionTo(project2, Phase.TESTING);
        Metrics metrics2 = new Metrics();
        metrics2.setTestCoverage(0.8);
        stateMachine.updateMetrics(project2, metrics2);

        // Verify states are isolated
        SDLCState retrieved1 = stateMachine.getCurrentState(project1);
        SDLCState retrieved2 = stateMachine.getCurrentState(project2);

        assertThat(retrieved1.getCurrentPhase()).isEqualTo(Phase.DEVELOPMENT);
        assertThat(retrieved1.getTestCoverage()).isEqualTo(0.5);

        assertThat(retrieved2.getCurrentPhase()).isEqualTo(Phase.TESTING);
        assertThat(retrieved2.getTestCoverage()).isEqualTo(0.8);
    }
}
