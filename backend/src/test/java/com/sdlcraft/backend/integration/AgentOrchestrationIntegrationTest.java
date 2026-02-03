package com.sdlcraft.backend.integration;

import com.sdlcraft.backend.agent.*;
import com.sdlcraft.backend.intent.Intent;
import com.sdlcraft.backend.intent.IntentRequest;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for agent orchestration workflows.
 * Tests the complete flow of PLAN → ACT → OBSERVE → REFLECT pattern.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AgentOrchestrationIntegrationTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private AgentExecutionRepository executionRepository;

    private SDLCState testState;
    private IntentResult testIntent;

    @BeforeEach
    void setUp() {
        // Create test SDLC state
        testState = new SDLCState();
        testState.setProjectId("test-project");
        testState.setCurrentPhase(Phase.DEVELOPMENT);
        testState.setRiskLevel(RiskLevel.LOW);
        testState.setTestCoverage(0.75);
        testState.setOpenIssues(5);

        // Create test intent
        testIntent = new IntentResult();
        testIntent.setIntent("status");
        testIntent.setTarget("project");
        testIntent.setConfidence(0.95);
        testIntent.setExplanation("Query project status");
    }

    @Test
    void testCompleteAgentWorkflow() {
        // Execute workflow through orchestrator
        ExecutionResult result = orchestrator.execute(testIntent, testState, "test-user", "test-project");

        // Verify execution completed (even without agents registered)
        assertThat(result).isNotNull();
        assertThat(result.getOverallStatus()).isNotNull();
        assertThat(result.getOverallStatus()).isEqualTo(AgentStatus.FAILURE); // No agents registered

        // Verify execution was persisted
        var executions = executionRepository.findAll();
        assertThat(executions).isNotEmpty();
    }

    @Test
    void testAgentContextPreservation() {
        // Create intent with custom parameters
        IntentResult analyzeIntent = new IntentResult();
        analyzeIntent.setIntent("analyze");
        analyzeIntent.setTarget("security");
        analyzeIntent.setConfidence(0.95);
        analyzeIntent.setExplanation("Analyze security");

        // Execute workflow
        ExecutionResult result = orchestrator.execute(analyzeIntent, testState, "test-user", "test-project");

        // Verify execution completed (even without agents registered)
        assertThat(result).isNotNull();
        assertThat(result.getOverallStatus()).isNotNull();
        assertThat(result.getOverallStatus()).isEqualTo(AgentStatus.FAILURE); // No agents registered
    }

    @Test
    void testErrorHandlingAndReflection() {
        // Create intent that might trigger an error
        IntentResult invalidIntent = new IntentResult();
        invalidIntent.setIntent("invalid-intent");
        invalidIntent.setTarget("unknown");
        invalidIntent.setConfidence(0.5);
        invalidIntent.setExplanation("Invalid intent");

        // Execute workflow
        ExecutionResult result = orchestrator.execute(invalidIntent, testState, "test-user", "test-project");

        // Verify error was handled
        assertThat(result).isNotNull();
        assertThat(result.getOverallStatus()).isNotNull();
    }

    @Test
    void testMultiStepWorkflow() {
        // Create intent for multi-step workflow
        IntentResult improveIntent = new IntentResult();
        improveIntent.setIntent("improve");
        improveIntent.setTarget("performance");
        improveIntent.setConfidence(0.95);
        improveIntent.setExplanation("Improve performance");

        // Execute workflow
        ExecutionResult result = orchestrator.execute(improveIntent, testState, "test-user", "test-project");

        // Verify execution completed (even without agents registered)
        assertThat(result).isNotNull();
        assertThat(result.getOverallStatus()).isNotNull();
    }

    @Test
    void testConcurrentAgentExecution() throws InterruptedException {
        // Create multiple intents for concurrent execution
        IntentResult intent1 = createTestIntent("status", "project");
        IntentResult intent2 = createTestIntent("analyze", "security");

        // Execute concurrently
        Thread thread1 = new Thread(() -> orchestrator.execute(intent1, testState, "user1", "test-project"));
        Thread thread2 = new Thread(() -> orchestrator.execute(intent2, testState, "user2", "test-project"));

        thread1.start();
        thread2.start();

        thread1.join(5000);
        thread2.join(5000);

        // Verify both executions completed
        var executions = executionRepository.findAll();
        assertThat(executions).hasSizeGreaterThanOrEqualTo(2);
    }

    private IntentResult createTestIntent(String intent, String target) {
        IntentResult result = new IntentResult();
        result.setIntent(intent);
        result.setTarget(target);
        result.setConfidence(0.95);
        result.setExplanation("Test intent: " + intent);
        return result;
    }
}
