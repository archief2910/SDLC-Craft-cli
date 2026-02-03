package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentPhase;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.agent.AgentStatus;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.memory.ExecutionStatistics;
import com.sdlcraft.backend.memory.LongTermMemory;
import com.sdlcraft.backend.sdlc.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Status Response Completeness.
 * 
 * Feature: sdlcraft-cli
 * Property 13: Status Response Completeness
 * Validates: Requirements 3.7, 10.4
 * 
 * This test verifies that for any status query, the Backend response includes
 * all required fields: project phase, risk level, test coverage, and release readiness.
 * 
 * The status response must be complete and contain all essential information
 * regardless of the project state or configuration.
 */
class StatusResponseCompletenessPropertyTest {
    
    private StatusIntentHandler statusHandler;
    private SDLCStateMachine mockStateMachine;
    private LongTermMemory mockMemory;
    
    @BeforeTry
    void setUp() {
        mockStateMachine = Mockito.mock(SDLCStateMachine.class);
        mockMemory = Mockito.mock(LongTermMemory.class);
        statusHandler = new StatusIntentHandler(mockStateMachine, mockMemory);
    }
    
    /**
     * Property 13: Status response contains all required fields
     * 
     * For any status query, the response must include:
     * - projectId
     * - currentPhase
     * - riskLevel
     * - testCoverage
     * - releaseReadiness
     * - readinessStatus
     */
    @Property(tries = 100)
    @Label("Status response contains all required fields")
    void statusResponseContainsAllRequiredFields(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 100) int openIssues,
            @ForAll @IntRange(min = 0, max = 60) int daysSinceDeployment) {
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, openIssues, daysSinceDeployment);
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, openIssues);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Verify result is successful
        assertThat(result.getStatus())
                .as("Status handler should succeed")
                .isEqualTo(AgentStatus.SUCCESS);
        
        // Extract status data
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData = (Map<String, Object>) result.getData().get("status");
        
        assertThat(statusData)
                .as("Status data should not be null")
                .isNotNull();
        
        // Verify all required fields are present
        assertThat(statusData)
                .as("Status response must contain projectId")
                .containsKey("projectId");
        
        assertThat(statusData)
                .as("Status response must contain currentPhase")
                .containsKey("currentPhase");
        
        assertThat(statusData)
                .as("Status response must contain riskLevel")
                .containsKey("riskLevel");
        
        assertThat(statusData)
                .as("Status response must contain testCoverage")
                .containsKey("testCoverage");
        
        assertThat(statusData)
                .as("Status response must contain releaseReadiness")
                .containsKey("releaseReadiness");
        
        assertThat(statusData)
                .as("Status response must contain readinessStatus")
                .containsKey("readinessStatus");
        
        // Verify field values are not null
        assertThat(statusData.get("projectId"))
                .as("projectId should not be null")
                .isNotNull();
        
        assertThat(statusData.get("currentPhase"))
                .as("currentPhase should not be null")
                .isNotNull();
        
        assertThat(statusData.get("riskLevel"))
                .as("riskLevel should not be null")
                .isNotNull();
        
        assertThat(statusData.get("testCoverage"))
                .as("testCoverage should not be null")
                .isNotNull();
        
        assertThat(statusData.get("releaseReadiness"))
                .as("releaseReadiness should not be null")
                .isNotNull();
        
        assertThat(statusData.get("readinessStatus"))
                .as("readinessStatus should not be null")
                .isNotNull();
    }
    
    /**
     * Property 13: Status response includes statistics
     * 
     * For any status query, the response must include execution statistics
     * with totalExecutions, successRate, averageDuration, and mostCommonIntent.
     */
    @Property(tries = 100)
    @Label("Status response includes execution statistics")
    void statusResponseIncludesStatistics(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, 5, 10);
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, 5);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Extract status data
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData = (Map<String, Object>) result.getData().get("status");
        
        // Verify statistics are present
        assertThat(statusData)
                .as("Status response must contain statistics")
                .containsKey("statistics");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> statisticsData = (Map<String, Object>) statusData.get("statistics");
        
        assertThat(statisticsData)
                .as("Statistics data should not be null")
                .isNotNull();
        
        // Verify statistics fields
        assertThat(statisticsData)
                .as("Statistics must contain totalExecutions")
                .containsKey("totalExecutions");
        
        assertThat(statisticsData)
                .as("Statistics must contain successRate")
                .containsKey("successRate");
        
        assertThat(statisticsData)
                .as("Statistics must contain averageDuration")
                .containsKey("averageDuration");
        
        assertThat(statisticsData)
                .as("Statistics must contain mostCommonIntent")
                .containsKey("mostCommonIntent");
    }
    
    /**
     * Property 13: Status response includes summary
     * 
     * For any status query, the response must include a human-readable summary.
     */
    @Property(tries = 100)
    @Label("Status response includes human-readable summary")
    void statusResponseIncludesSummary(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, 5, 10);
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, 5);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Verify summary is present
        assertThat(result.getData())
                .as("Result data must contain summary")
                .containsKey("summary");
        
        String summary = (String) result.getData().get("summary");
        
        assertThat(summary)
                .as("Summary should not be null or empty")
                .isNotNull()
                .isNotEmpty();
        
        // Verify summary contains key information
        assertThat(summary)
                .as("Summary should mention phase")
                .contains(phase.toString());
        
        assertThat(summary)
                .as("Summary should mention risk level")
                .contains(riskLevel.toString());
    }
    
    /**
     * Property 13: Status response includes reasoning
     * 
     * For any status query, the response must include reasoning explaining
     * the current state assessment.
     */
    @Property(tries = 100)
    @Label("Status response includes reasoning")
    void statusResponseIncludesReasoning(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, 5, 10);
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, 5);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Verify reasoning is present
        assertThat(result.getReasoning())
                .as("Status response must include reasoning")
                .isNotNull()
                .isNotEmpty();
        
        // Verify reasoning mentions key state information
        assertThat(result.getReasoning())
                .as("Reasoning should mention phase")
                .contains(phase.toString());
        
        assertThat(result.getReasoning())
                .as("Reasoning should mention risk level")
                .contains(riskLevel.toString());
    }
    
    /**
     * Property 13: Verbose mode includes additional details
     * 
     * For any status query with verbose mode enabled, the response must include
     * additional details like customMetrics, readinessFactors, and blockers.
     */
    @Property(tries = 100)
    @Label("Verbose mode includes additional details")
    void verboseModeIncludesAdditionalDetails(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state with custom metrics
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, 5, 10);
        Map<String, Object> customMetrics = new HashMap<>();
        customMetrics.put("custom_metric_1", 42);
        customMetrics.put("custom_metric_2", "value");
        state.setCustomMetrics(customMetrics);
        
        // Create release readiness with factors
        List<String> readyFactors = Arrays.asList("High test coverage", "Recent deployment");
        List<String> blockingFactors = Arrays.asList("Open critical issues");
        ReleaseReadiness readiness = new ReleaseReadiness(
                testCoverage * 0.9,
                ReleaseReadiness.ReadinessStatus.fromScore(testCoverage * 0.9),
                readyFactors,
                blockingFactors
        );
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context with verbose mode
        AgentContext context = createAgentContextWithVerbose(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Extract status data
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData = (Map<String, Object>) result.getData().get("status");
        
        // Verify verbose fields are present
        assertThat(statusData)
                .as("Verbose mode must include customMetrics")
                .containsKey("customMetrics");
        
        assertThat(statusData)
                .as("Verbose mode must include readinessFactors")
                .containsKey("readinessFactors");
        
        assertThat(statusData)
                .as("Verbose mode must include blockers")
                .containsKey("blockers");
        
        // Verify custom metrics are included
        @SuppressWarnings("unchecked")
        Map<String, Object> returnedCustomMetrics = (Map<String, Object>) statusData.get("customMetrics");
        
        assertThat(returnedCustomMetrics)
                .as("Custom metrics should be included")
                .isNotNull()
                .containsKeys("custom_metric_1", "custom_metric_2");
    }
    
    /**
     * Property 13: Status response is consistent across multiple calls
     * 
     * For any project state, calling the status handler multiple times
     * should return consistent field structure (though values may differ).
     */
    @Property(tries = 50)
    @Label("Status response structure is consistent")
    void statusResponseStructureIsConsistent(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state
        SDLCState state = createSDLCState(projectId, phase, riskLevel, testCoverage, 5, 10);
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, 5);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler twice
        AgentResult result1 = statusHandler.handle(context);
        AgentResult result2 = statusHandler.handle(context);
        
        // Extract status data from both results
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData1 = (Map<String, Object>) result1.getData().get("status");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData2 = (Map<String, Object>) result2.getData().get("status");
        
        // Verify both responses have the same keys
        assertThat(statusData1.keySet())
                .as("Multiple calls should return same field structure")
                .isEqualTo(statusData2.keySet());
    }
    
    /**
     * Property 13: Status response handles missing optional fields gracefully
     * 
     * For any project state where optional fields (like lastDeployment) are missing,
     * the status response should still include all required fields.
     */
    @Property(tries = 100)
    @Label("Status response handles missing optional fields")
    void statusResponseHandlesMissingOptionalFields(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create SDLC state without lastDeployment
        SDLCState state = new SDLCState(projectId, phase);
        state.setRiskLevel(riskLevel);
        state.setTestCoverage(testCoverage);
        state.setOpenIssues(5);
        state.setLastDeployment(null); // Explicitly null
        
        // Create release readiness
        ReleaseReadiness readiness = createReleaseReadiness(testCoverage, 5);
        
        // Create execution statistics
        ExecutionStatistics stats = createExecutionStatistics(projectId);
        
        // Mock dependencies
        when(mockStateMachine.getCurrentState(projectId)).thenReturn(state);
        when(mockStateMachine.calculateReadiness(projectId)).thenReturn(readiness);
        when(mockMemory.getStatistics(projectId)).thenReturn(stats);
        
        // Create agent context
        AgentContext context = createAgentContext(projectId);
        
        // Execute status handler
        AgentResult result = statusHandler.handle(context);
        
        // Verify result is successful
        assertThat(result.getStatus())
                .as("Status handler should succeed even with missing optional fields")
                .isEqualTo(AgentStatus.SUCCESS);
        
        // Extract status data
        @SuppressWarnings("unchecked")
        Map<String, Object> statusData = (Map<String, Object>) result.getData().get("status");
        
        // Verify all required fields are still present
        assertThat(statusData)
                .as("Required fields must be present even when optional fields are missing")
                .containsKeys("projectId", "currentPhase", "riskLevel", 
                             "testCoverage", "releaseReadiness", "readinessStatus");
        
        // Verify lastDeployment is not in the response when null
        assertThat(statusData)
                .as("Optional null fields should not be included")
                .doesNotContainKey("lastDeployment");
    }
    
    // Helper methods
    
    private SDLCState createSDLCState(String projectId, Phase phase, RiskLevel riskLevel,
                                     double testCoverage, int openIssues, int daysSinceDeployment) {
        SDLCState state = new SDLCState(projectId, phase);
        state.setRiskLevel(riskLevel);
        state.setTestCoverage(testCoverage);
        state.setOpenIssues(openIssues);
        state.setLastDeployment(LocalDateTime.now().minus(daysSinceDeployment, ChronoUnit.DAYS));
        state.setCustomMetrics(new HashMap<>());
        return state;
    }
    
    private ReleaseReadiness createReleaseReadiness(double testCoverage, int openIssues) {
        double score = testCoverage * 0.8; // Simplified calculation
        ReleaseReadiness.ReadinessStatus status = ReleaseReadiness.ReadinessStatus.fromScore(score);
        List<String> readyFactors = new ArrayList<>();
        List<String> blockingFactors = new ArrayList<>();
        
        if (testCoverage > 0.8) {
            readyFactors.add("High test coverage");
        }
        if (openIssues > 10) {
            blockingFactors.add("Many open issues");
        }
        
        return new ReleaseReadiness(score, status, readyFactors, blockingFactors);
    }
    
    private ExecutionStatistics createExecutionStatistics(String projectId) {
        return new ExecutionStatistics(
                projectId,
                100L,  // totalExecutions
                85L,   // successfulExecutions
                10L,   // failedExecutions
                5L,    // partialExecutions
                0.85,  // successRate
                1500L, // averageDurationMs
                "status" // mostCommonIntent
        );
    }
    
    private AgentContext createAgentContext(String projectId) {
        IntentResult intent = new IntentResult("status", null, 1.0, "Status query");
        
        return new AgentContext.Builder()
                .executionId("exec-" + UUID.randomUUID().toString())
                .projectId(projectId)
                .intent(intent)
                .build();
    }
    
    private AgentContext createAgentContextWithVerbose(String projectId) {
        IntentResult intent = new IntentResult("status", null, 1.0, "Status query");
        
        return new AgentContext.Builder()
                .executionId("exec-" + UUID.randomUUID().toString())
                .projectId(projectId)
                .intent(intent)
                .parameter("verbose", "true")
                .build();
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<String> projectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "project-" + s);
    }
}
