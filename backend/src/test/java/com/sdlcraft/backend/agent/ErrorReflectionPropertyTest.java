package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Error Reflection.
 * 
 * Feature: sdlcraft-cli
 * Property 16: Error Reflection
 * Validates: Requirements 4.6
 * 
 * This test verifies that for any agent execution that encounters an error,
 * the ReflectionAgent is invoked to analyze the failure and suggest recovery actions.
 */
class ErrorReflectionPropertyTest {
    
    /**
     * Property 16: Error Reflection
     * 
     * For any agent execution that encounters an error, the ReflectionAgent should be
     * invoked to analyze the failure and suggest recovery actions.
     */
    @Property(tries = 100)
    @Label("ReflectionAgent is invoked when any agent encounters an error")
    void reflectionAgentInvokedOnError(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("failurePhases") AgentPhase failurePhase,
            @ForAll("errorMessages") String errorMessage) {
        
        // Create orchestrator with mock repository
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create agents where one will fail
        FailingAgent failingAgent = new FailingAgent(getAgentTypeForPhase(failurePhase), failurePhase, errorMessage);
        TrackingReflectionAgent reflectionAgent = new TrackingReflectionAgent();
        
        // Register all agents
        orchestrator.registerAgent(new SuccessfulAgent("planner"));
        orchestrator.registerAgent(new SuccessfulAgent("executor"));
        orchestrator.registerAgent(new SuccessfulAgent("validator"));
        orchestrator.registerAgent(reflectionAgent);
        
        // Replace the agent that should fail
        orchestrator.registerAgent(failingAgent);
        
        // Execute intent
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Verify execution completed (even with failure)
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isNotNull();
        
        // Verify ReflectionAgent was invoked
        assertThat(reflectionAgent.wasInvoked())
                .as("ReflectionAgent should be invoked even when errors occur")
                .isTrue();
        
        // Verify ReflectionAgent received context with the failure
        assertThat(reflectionAgent.getLastContextSize())
                .as("ReflectionAgent should have access to previous results including the failure")
                .isGreaterThan(0);
        
        // Verify ReflectionAgent analyzed the failure
        List<AgentResult> previousResults = reflectionAgent.getLastPreviousResults();
        boolean foundFailure = previousResults.stream()
                .anyMatch(r -> r.isFailure());
        
        assertThat(foundFailure)
                .as("ReflectionAgent should have access to the failed agent result")
                .isTrue();
        
        // Verify ReflectionAgent produced a result
        AgentResult reflectionResult = result.getAgentResults().stream()
                .filter(r -> "reflection".equals(r.getAgentType()))
                .findFirst()
                .orElse(null);
        
        assertThat(reflectionResult)
                .as("ReflectionAgent should produce a result")
                .isNotNull();
        
        assertThat(reflectionResult.getPhase())
                .as("ReflectionAgent should execute in REFLECT phase")
                .isEqualTo(AgentPhase.REFLECT);
    }
    
    /**
     * Property 16 (Recovery Actions): ReflectionAgent suggests recovery actions on failure
     * 
     * For any agent failure, the ReflectionAgent should analyze the failure and
     * provide recovery action suggestions.
     */
    @Property(tries = 100)
    @Label("ReflectionAgent suggests recovery actions on failure")
    void reflectionAgentSuggestsRecoveryActions(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("failurePhases") AgentPhase failurePhase,
            @ForAll("errorMessages") String errorMessage) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create failing agent and real reflection agent
        FailingAgent failingAgent = new FailingAgent(getAgentTypeForPhase(failurePhase), failurePhase, errorMessage);
        ReflectionAgent reflectionAgent = new ReflectionAgent();
        
        // Register agents
        orchestrator.registerAgent(new SuccessfulAgent("planner"));
        orchestrator.registerAgent(new SuccessfulAgent("executor"));
        orchestrator.registerAgent(new SuccessfulAgent("validator"));
        orchestrator.registerAgent(reflectionAgent);
        orchestrator.registerAgent(failingAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Find reflection result
        AgentResult reflectionResult = result.getAgentResults().stream()
                .filter(r -> "reflection".equals(r.getAgentType()))
                .findFirst()
                .orElse(null);
        
        assertThat(reflectionResult).isNotNull();
        
        // Verify reflection result contains recovery actions
        Object recoveryActionsObj = reflectionResult.getData("recoveryActions");
        assertThat(recoveryActionsObj)
                .as("ReflectionAgent should provide recovery actions")
                .isNotNull()
                .isInstanceOf(List.class);
        
        @SuppressWarnings("unchecked")
        List<String> recoveryActions = (List<String>) recoveryActionsObj;
        
        assertThat(recoveryActions)
                .as("Recovery actions should not be empty")
                .isNotEmpty();
        
        // Verify recovery actions are actionable (contain verbs)
        assertThat(recoveryActions)
                .as("Recovery actions should be actionable strings")
                .allMatch(action -> action != null && !action.trim().isEmpty());
    }
    
    /**
     * Property 16 (Failure Analysis): ReflectionAgent analyzes failure root cause
     * 
     * For any agent failure, the ReflectionAgent should identify and report
     * the failure phase and agent type.
     */
    @Property(tries = 100)
    @Label("ReflectionAgent analyzes failure root cause")
    void reflectionAgentAnalyzesFailureRootCause(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("failurePhases") AgentPhase failurePhase,
            @ForAll("errorMessages") String errorMessage) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create failing agent and real reflection agent
        String failingAgentType = getAgentTypeForPhase(failurePhase);
        FailingAgent failingAgent = new FailingAgent(failingAgentType, failurePhase, errorMessage);
        ReflectionAgent reflectionAgent = new ReflectionAgent();
        
        // Register agents
        orchestrator.registerAgent(new SuccessfulAgent("planner"));
        orchestrator.registerAgent(new SuccessfulAgent("executor"));
        orchestrator.registerAgent(new SuccessfulAgent("validator"));
        orchestrator.registerAgent(reflectionAgent);
        orchestrator.registerAgent(failingAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Find reflection result
        AgentResult reflectionResult = result.getAgentResults().stream()
                .filter(r -> "reflection".equals(r.getAgentType()))
                .findFirst()
                .orElse(null);
        
        assertThat(reflectionResult).isNotNull();
        
        // Verify analysis contains failure information
        Object analysisObj = reflectionResult.getData("analysis");
        assertThat(analysisObj)
                .as("ReflectionAgent should provide execution analysis")
                .isNotNull()
                .isInstanceOf(ReflectionAgent.ExecutionAnalysis.class);
        
        ReflectionAgent.ExecutionAnalysis analysis = (ReflectionAgent.ExecutionAnalysis) analysisObj;
        
        assertThat(analysis.getOverallOutcome())
                .as("Analysis should identify execution as FAILURE")
                .isEqualTo("FAILURE");
        
        assertThat(analysis.getFailedPhases())
                .as("Analysis should count failed phases")
                .isGreaterThan(0);
        
        assertThat(analysis.getFailurePhase())
                .as("Analysis should identify the failure phase")
                .isEqualTo(failurePhase);
        
        assertThat(analysis.getPrimaryFailure())
                .as("Analysis should identify primary failure")
                .isNotNull();
        
        assertThat(analysis.getPrimaryFailure().getAgentType())
                .as("Analysis should identify failing agent type")
                .isEqualTo(failingAgentType);
        
        assertThat(analysis.getPrimaryFailure().getError())
                .as("Analysis should capture error message")
                .isEqualTo(errorMessage);
    }
    
    /**
     * Property 16 (Insights Generation): ReflectionAgent generates insights from failures
     * 
     * For any agent failure, the ReflectionAgent should generate insights
     * about what went wrong and why.
     */
    @Property(tries = 100)
    @Label("ReflectionAgent generates insights from failures")
    void reflectionAgentGeneratesInsights(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("failurePhases") AgentPhase failurePhase,
            @ForAll("errorMessages") String errorMessage) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create failing agent and real reflection agent
        FailingAgent failingAgent = new FailingAgent(getAgentTypeForPhase(failurePhase), failurePhase, errorMessage);
        ReflectionAgent reflectionAgent = new ReflectionAgent();
        
        // Register agents
        orchestrator.registerAgent(new SuccessfulAgent("planner"));
        orchestrator.registerAgent(new SuccessfulAgent("executor"));
        orchestrator.registerAgent(new SuccessfulAgent("validator"));
        orchestrator.registerAgent(reflectionAgent);
        orchestrator.registerAgent(failingAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Find reflection result
        AgentResult reflectionResult = result.getAgentResults().stream()
                .filter(r -> "reflection".equals(r.getAgentType()))
                .findFirst()
                .orElse(null);
        
        assertThat(reflectionResult).isNotNull();
        
        // Verify insights are provided
        Object insightsObj = reflectionResult.getData("insights");
        assertThat(insightsObj)
                .as("ReflectionAgent should provide insights")
                .isNotNull()
                .isInstanceOf(List.class);
        
        @SuppressWarnings("unchecked")
        List<String> insights = (List<String>) insightsObj;
        
        assertThat(insights)
                .as("Insights should not be empty")
                .isNotEmpty();
        
        // Verify insights mention the failure
        boolean mentionsFailure = insights.stream()
                .anyMatch(insight -> insight.toLowerCase().contains("fail") || 
                                   insight.toLowerCase().contains("error"));
        
        assertThat(mentionsFailure)
                .as("Insights should mention the failure")
                .isTrue();
    }
    
    /**
     * Property 16 (Recommendations): ReflectionAgent provides recommendations
     * 
     * For any agent failure, the ReflectionAgent should provide recommendations
     * for preventing similar failures in the future.
     */
    @Property(tries = 100)
    @Label("ReflectionAgent provides recommendations for future executions")
    void reflectionAgentProvidesRecommendations(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("failurePhases") AgentPhase failurePhase,
            @ForAll("errorMessages") String errorMessage) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create failing agent and real reflection agent
        FailingAgent failingAgent = new FailingAgent(getAgentTypeForPhase(failurePhase), failurePhase, errorMessage);
        ReflectionAgent reflectionAgent = new ReflectionAgent();
        
        // Register agents
        orchestrator.registerAgent(new SuccessfulAgent("planner"));
        orchestrator.registerAgent(new SuccessfulAgent("executor"));
        orchestrator.registerAgent(new SuccessfulAgent("validator"));
        orchestrator.registerAgent(reflectionAgent);
        orchestrator.registerAgent(failingAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Find reflection result
        AgentResult reflectionResult = result.getAgentResults().stream()
                .filter(r -> "reflection".equals(r.getAgentType()))
                .findFirst()
                .orElse(null);
        
        assertThat(reflectionResult).isNotNull();
        
        // Verify recommendations are provided
        Object recommendationsObj = reflectionResult.getData("recommendations");
        assertThat(recommendationsObj)
                .as("ReflectionAgent should provide recommendations")
                .isNotNull()
                .isInstanceOf(List.class);
        
        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) recommendationsObj;
        
        assertThat(recommendations)
                .as("Recommendations should not be empty")
                .isNotEmpty();
        
        // Verify recommendations are actionable
        assertThat(recommendations)
                .as("Recommendations should be actionable strings")
                .allMatch(rec -> rec != null && !rec.trim().isEmpty());
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<IntentResult> intentResults() {
        return Combinators.combine(
                validIntents(),
                validTargets(),
                Arbitraries.doubles().between(0.7, 1.0)
        ).as((intent, target, confidence) -> {
            IntentResult result = new IntentResult();
            result.setIntent(intent);
            result.setTarget(target);
            result.setConfidence(confidence);
            result.setExplanation("Generated intent for testing");
            result.setModifiers(new HashMap<>());
            return result;
        });
    }
    
    @Provide
    Arbitrary<String> validIntents() {
        return Arbitraries.of("status", "analyze", "improve", "test", "debug", "prepare", "release");
    }
    
    @Provide
    Arbitrary<String> validTargets() {
        return Arbitraries.of("security", "performance", "quality", "unit", "staging", "production", "");
    }
    
    @Provide
    Arbitrary<SDLCState> sdlcStates() {
        return Combinators.combine(
                projectIds(),
                Arbitraries.of(Phase.values()),
                Arbitraries.of(RiskLevel.values()),
                Arbitraries.doubles().between(0.0, 1.0),
                Arbitraries.integers().between(0, 50)
        ).as((projectId, phase, riskLevel, coverage, issues) -> {
            SDLCState state = new SDLCState(projectId, phase);
            state.setRiskLevel(riskLevel);
            state.setTestCoverage(coverage);
            state.setOpenIssues(issues);
            state.setTotalIssues(100);
            state.setLastDeployment(LocalDateTime.now().minusDays(7));
            return state;
        });
    }
    
    @Provide
    Arbitrary<String> projectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(15)
                .map(s -> "project-" + s);
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(15)
                .map(s -> "user-" + s);
    }
    
    @Provide
    Arbitrary<AgentPhase> failurePhases() {
        // Reflection doesn't fail, so only test PLAN, ACT, OBSERVE
        return Arbitraries.of(AgentPhase.PLAN, AgentPhase.ACT, AgentPhase.OBSERVE);
    }
    
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "Resource not found",
                "Permission denied",
                "Network timeout",
                "Invalid configuration",
                "Service unavailable",
                "Validation failed",
                "Execution timeout",
                "Insufficient resources"
        );
    }
    
    // Helper methods
    
    private String getAgentTypeForPhase(AgentPhase phase) {
        switch (phase) {
            case PLAN:
                return "planner";
            case ACT:
                return "executor";
            case OBSERVE:
                return "validator";
            case REFLECT:
                return "reflection";
            default:
                return "unknown";
        }
    }
    
    // Helper classes for testing
    
    /**
     * Agent that always succeeds.
     */
    private static class SuccessfulAgent implements Agent {
        private final String type;
        
        public SuccessfulAgent(String type) {
            this.type = type;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public AgentResult plan(AgentContext context) {
            return createSuccessResult(AgentPhase.PLAN);
        }
        
        @Override
        public AgentResult act(AgentContext context) {
            return createSuccessResult(AgentPhase.ACT);
        }
        
        @Override
        public AgentResult observe(AgentContext context) {
            return createSuccessResult(AgentPhase.OBSERVE);
        }
        
        @Override
        public AgentResult reflect(AgentContext context) {
            return createSuccessResult(AgentPhase.REFLECT);
        }
        
        @Override
        public boolean canHandle(AgentContext context) {
            return true;
        }
        
        private AgentResult createSuccessResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(phase)
                    .status(AgentStatus.SUCCESS)
                    .reasoning("Successful execution")
                    .build();
        }
    }
    
    /**
     * Agent that fails at a specific phase.
     */
    private static class FailingAgent implements Agent {
        private final String type;
        private final AgentPhase failurePhase;
        private final String errorMessage;
        
        public FailingAgent(String type, AgentPhase failurePhase, String errorMessage) {
            this.type = type;
            this.failurePhase = failurePhase;
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public AgentResult plan(AgentContext context) {
            if (failurePhase == AgentPhase.PLAN) {
                return createFailureResult(AgentPhase.PLAN);
            }
            return createSuccessResult(AgentPhase.PLAN);
        }
        
        @Override
        public AgentResult act(AgentContext context) {
            if (failurePhase == AgentPhase.ACT) {
                return createFailureResult(AgentPhase.ACT);
            }
            return createSuccessResult(AgentPhase.ACT);
        }
        
        @Override
        public AgentResult observe(AgentContext context) {
            if (failurePhase == AgentPhase.OBSERVE) {
                return createFailureResult(AgentPhase.OBSERVE);
            }
            return createSuccessResult(AgentPhase.OBSERVE);
        }
        
        @Override
        public AgentResult reflect(AgentContext context) {
            if (failurePhase == AgentPhase.REFLECT) {
                return createFailureResult(AgentPhase.REFLECT);
            }
            return createSuccessResult(AgentPhase.REFLECT);
        }
        
        @Override
        public boolean canHandle(AgentContext context) {
            return true;
        }
        
        private AgentResult createSuccessResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(phase)
                    .status(AgentStatus.SUCCESS)
                    .reasoning("Successful execution")
                    .build();
        }
        
        private AgentResult createFailureResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(phase)
                    .status(AgentStatus.FAILURE)
                    .error(errorMessage)
                    .reasoning("Execution failed: " + errorMessage)
                    .build();
        }
    }
    
    /**
     * Tracking reflection agent that records invocation details.
     */
    private static class TrackingReflectionAgent implements Agent {
        private boolean invoked = false;
        private int lastContextSize = 0;
        private List<AgentResult> lastPreviousResults = new ArrayList<>();
        
        @Override
        public String getType() {
            return "reflection";
        }
        
        @Override
        public AgentResult plan(AgentContext context) {
            return createSkippedResult(AgentPhase.PLAN);
        }
        
        @Override
        public AgentResult act(AgentContext context) {
            return createSkippedResult(AgentPhase.ACT);
        }
        
        @Override
        public AgentResult observe(AgentContext context) {
            return createSkippedResult(AgentPhase.OBSERVE);
        }
        
        @Override
        public AgentResult reflect(AgentContext context) {
            invoked = true;
            lastContextSize = context.getPreviousResults().size();
            lastPreviousResults = new ArrayList<>(context.getPreviousResults());
            
            return AgentResult.builder()
                    .agentType("reflection")
                    .phase(AgentPhase.REFLECT)
                    .status(AgentStatus.SUCCESS)
                    .reasoning("Reflection completed")
                    .build();
        }
        
        @Override
        public boolean canHandle(AgentContext context) {
            return true;
        }
        
        private AgentResult createSkippedResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType("reflection")
                    .phase(phase)
                    .status(AgentStatus.SKIPPED)
                    .reasoning("Skipped")
                    .build();
        }
        
        public boolean wasInvoked() {
            return invoked;
        }
        
        public int getLastContextSize() {
            return lastContextSize;
        }
        
        public List<AgentResult> getLastPreviousResults() {
            return lastPreviousResults;
        }
    }
}
