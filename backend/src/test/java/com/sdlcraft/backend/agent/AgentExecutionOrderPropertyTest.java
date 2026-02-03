package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Agent Execution Order and Context Preservation.
 * 
 * Feature: sdlcraft-cli
 * Property 14: Agent Execution Order with Context Preservation
 * Validates: Requirements 4.1, 4.4, 4.5
 * 
 * This test verifies that for any multi-step intent execution, agents are invoked
 * in the order PLAN → ACT → OBSERVE → REFLECT, and execution context is available
 * to all agents in the sequence.
 */
class AgentExecutionOrderPropertyTest {
    
    /**
     * Property 14: Agent Execution Order with Context Preservation
     * 
     * For any multi-step intent execution, agents should be invoked in the order
     * PLAN → ACT → OBSERVE → REFLECT, and execution context should be available
     * to all agents in the sequence.
     */
    @Property(tries = 100)
    @Label("Agents execute in PLAN → ACT → OBSERVE → REFLECT order with context preservation")
    void agentExecutionOrderWithContextPreservation(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId) {
        
        // Create orchestrator with mock repository
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create and register tracking agents
        TrackingAgent plannerAgent = new TrackingAgent("planner");
        TrackingAgent executorAgent = new TrackingAgent("executor");
        TrackingAgent validatorAgent = new TrackingAgent("validator");
        TrackingAgent reflectionAgent = new TrackingAgent("reflection");
        
        orchestrator.registerAgent(plannerAgent);
        orchestrator.registerAgent(executorAgent);
        orchestrator.registerAgent(validatorAgent);
        orchestrator.registerAgent(reflectionAgent);
        
        // Execute intent
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Verify execution completed
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isNotNull();
        
        // Verify all four phases were executed
        List<AgentResult> agentResults = result.getAgentResults();
        assertThat(agentResults)
                .as("Should have results from all four agent phases")
                .hasSize(4);
        
        // Verify execution order: PLAN → ACT → OBSERVE → REFLECT
        assertThat(agentResults.get(0).getPhase())
                .as("First phase should be PLAN")
                .isEqualTo(AgentPhase.PLAN);
        assertThat(agentResults.get(0).getAgentType())
                .as("First agent should be planner")
                .isEqualTo("planner");
        
        assertThat(agentResults.get(1).getPhase())
                .as("Second phase should be ACT")
                .isEqualTo(AgentPhase.ACT);
        assertThat(agentResults.get(1).getAgentType())
                .as("Second agent should be executor")
                .isEqualTo("executor");
        
        assertThat(agentResults.get(2).getPhase())
                .as("Third phase should be OBSERVE")
                .isEqualTo(AgentPhase.OBSERVE);
        assertThat(agentResults.get(2).getAgentType())
                .as("Third agent should be validator")
                .isEqualTo("validator");
        
        assertThat(agentResults.get(3).getPhase())
                .as("Fourth phase should be REFLECT")
                .isEqualTo(AgentPhase.REFLECT);
        assertThat(agentResults.get(3).getAgentType())
                .as("Fourth agent should be reflection")
                .isEqualTo("reflection");
        
        // Verify context preservation: each agent should have access to previous results
        assertThat(plannerAgent.getLastContextSize())
                .as("Planner should start with no previous results")
                .isEqualTo(0);
        
        assertThat(executorAgent.getLastContextSize())
                .as("Executor should have access to planner result")
                .isEqualTo(1);
        
        assertThat(validatorAgent.getLastContextSize())
                .as("Validator should have access to planner and executor results")
                .isEqualTo(2);
        
        assertThat(reflectionAgent.getLastContextSize())
                .as("Reflection should have access to all previous results")
                .isEqualTo(3);
        
        // Verify context contains correct intent and state
        assertThat(plannerAgent.getLastIntent())
                .as("All agents should receive the same intent")
                .isEqualTo(intent);
        assertThat(executorAgent.getLastIntent()).isEqualTo(intent);
        assertThat(validatorAgent.getLastIntent()).isEqualTo(intent);
        assertThat(reflectionAgent.getLastIntent()).isEqualTo(intent);
        
        assertThat(plannerAgent.getLastState())
                .as("All agents should receive the same state")
                .isEqualTo(state);
        assertThat(executorAgent.getLastState()).isEqualTo(state);
        assertThat(validatorAgent.getLastState()).isEqualTo(state);
        assertThat(reflectionAgent.getLastState()).isEqualTo(state);
    }
    
    /**
     * Property 14 (Context Accumulation): Context accumulates results through execution
     * 
     * For any agent execution, the context should accumulate results from each phase,
     * making them available to subsequent agents.
     */
    @Property(tries = 100)
    @Label("Context accumulates results through execution phases")
    void contextAccumulatesResults(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Register tracking agents
        TrackingAgent plannerAgent = new TrackingAgent("planner");
        TrackingAgent executorAgent = new TrackingAgent("executor");
        TrackingAgent validatorAgent = new TrackingAgent("validator");
        TrackingAgent reflectionAgent = new TrackingAgent("reflection");
        
        orchestrator.registerAgent(plannerAgent);
        orchestrator.registerAgent(executorAgent);
        orchestrator.registerAgent(validatorAgent);
        orchestrator.registerAgent(reflectionAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Verify each agent could access previous results
        List<AgentResult> previousResults = executorAgent.getLastPreviousResults();
        assertThat(previousResults)
                .as("Executor should have access to planner result")
                .hasSize(1);
        assertThat(previousResults.get(0).getAgentType()).isEqualTo("planner");
        assertThat(previousResults.get(0).getPhase()).isEqualTo(AgentPhase.PLAN);
        
        previousResults = validatorAgent.getLastPreviousResults();
        assertThat(previousResults)
                .as("Validator should have access to planner and executor results")
                .hasSize(2);
        assertThat(previousResults.get(0).getAgentType()).isEqualTo("planner");
        assertThat(previousResults.get(1).getAgentType()).isEqualTo("executor");
        
        previousResults = reflectionAgent.getLastPreviousResults();
        assertThat(previousResults)
                .as("Reflection should have access to all previous results")
                .hasSize(3);
        assertThat(previousResults.get(0).getAgentType()).isEqualTo("planner");
        assertThat(previousResults.get(1).getAgentType()).isEqualTo("executor");
        assertThat(previousResults.get(2).getAgentType()).isEqualTo("validator");
    }
    
    /**
     * Property 14 (Data Flow): Data flows from one agent to the next through context
     * 
     * For any agent execution, data produced by one agent should be accessible
     * to subsequent agents through the context.
     */
    @Property(tries = 100)
    @Label("Data flows from one agent to the next through context")
    void dataFlowsThroughContext(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId,
            @ForAll("dataKeys") String dataKey,
            @ForAll("dataValues") String dataValue) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        // Create agent that produces data
        DataProducingAgent plannerAgent = new DataProducingAgent("planner", dataKey, dataValue);
        TrackingAgent executorAgent = new TrackingAgent("executor");
        TrackingAgent validatorAgent = new TrackingAgent("validator");
        TrackingAgent reflectionAgent = new TrackingAgent("reflection");
        
        orchestrator.registerAgent(plannerAgent);
        orchestrator.registerAgent(executorAgent);
        orchestrator.registerAgent(validatorAgent);
        orchestrator.registerAgent(reflectionAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Verify data produced by planner is accessible to subsequent agents
        AgentResult plannerResult = executorAgent.getLastPreviousResults().get(0);
        assertThat(plannerResult.getData(dataKey))
                .as("Data produced by planner should be accessible to executor")
                .isEqualTo(dataValue);
        
        plannerResult = validatorAgent.getLastPreviousResults().get(0);
        assertThat(plannerResult.getData(dataKey))
                .as("Data produced by planner should be accessible to validator")
                .isEqualTo(dataValue);
        
        plannerResult = reflectionAgent.getLastPreviousResults().get(0);
        assertThat(plannerResult.getData(dataKey))
                .as("Data produced by planner should be accessible to reflection")
                .isEqualTo(dataValue);
    }
    
    /**
     * Property 14 (Execution ID Consistency): Same execution ID is maintained across all agents
     * 
     * For any agent execution, all agents should receive the same execution ID
     * in their context.
     */
    @Property(tries = 100)
    @Label("Same execution ID is maintained across all agents")
    void executionIdConsistency(
            @ForAll("intentResults") IntentResult intent,
            @ForAll("sdlcStates") SDLCState state,
            @ForAll("userIds") String userId,
            @ForAll("projectIds") String projectId) {
        
        AgentExecutionRepository mockRepository = Mockito.mock(AgentExecutionRepository.class);
        when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(mockRepository);
        
        TrackingAgent plannerAgent = new TrackingAgent("planner");
        TrackingAgent executorAgent = new TrackingAgent("executor");
        TrackingAgent validatorAgent = new TrackingAgent("validator");
        TrackingAgent reflectionAgent = new TrackingAgent("reflection");
        
        orchestrator.registerAgent(plannerAgent);
        orchestrator.registerAgent(executorAgent);
        orchestrator.registerAgent(validatorAgent);
        orchestrator.registerAgent(reflectionAgent);
        
        // Execute
        ExecutionResult result = orchestrator.execute(intent, state, userId, projectId);
        
        // Verify all agents received the same execution ID
        String executionId = plannerAgent.getLastExecutionId();
        assertThat(executionId)
                .as("Execution ID should be set")
                .isNotNull()
                .isNotBlank();
        
        assertThat(executorAgent.getLastExecutionId())
                .as("Executor should receive same execution ID")
                .isEqualTo(executionId);
        
        assertThat(validatorAgent.getLastExecutionId())
                .as("Validator should receive same execution ID")
                .isEqualTo(executionId);
        
        assertThat(reflectionAgent.getLastExecutionId())
                .as("Reflection should receive same execution ID")
                .isEqualTo(executionId);
        
        assertThat(result.getExecutionId())
                .as("Result should have same execution ID")
                .isEqualTo(executionId);
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
    Arbitrary<String> dataKeys() {
        return Arbitraries.of("plan", "result", "findings", "metrics", "output");
    }
    
    @Provide
    Arbitrary<String> dataValues() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20);
    }
    
    // Helper classes for testing
    
    /**
     * Tracking agent that records context information for verification.
     */
    private static class TrackingAgent implements Agent {
        private final String type;
        private String lastExecutionId;
        private IntentResult lastIntent;
        private SDLCState lastState;
        private int lastContextSize;
        private List<AgentResult> lastPreviousResults;
        
        public TrackingAgent(String type) {
            this.type = type;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public AgentResult plan(AgentContext context) {
            recordContext(context);
            return createResult(AgentPhase.PLAN);
        }
        
        @Override
        public AgentResult act(AgentContext context) {
            recordContext(context);
            return createResult(AgentPhase.ACT);
        }
        
        @Override
        public AgentResult observe(AgentContext context) {
            recordContext(context);
            return createResult(AgentPhase.OBSERVE);
        }
        
        @Override
        public AgentResult reflect(AgentContext context) {
            recordContext(context);
            return createResult(AgentPhase.REFLECT);
        }
        
        @Override
        public boolean canHandle(AgentContext context) {
            return true;
        }
        
        private void recordContext(AgentContext context) {
            this.lastExecutionId = context.getExecutionId();
            this.lastIntent = context.getIntent();
            this.lastState = context.getCurrentState();
            this.lastContextSize = context.getPreviousResults().size();
            this.lastPreviousResults = new ArrayList<>(context.getPreviousResults());
        }
        
        private AgentResult createResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(phase)
                    .status(AgentStatus.SUCCESS)
                    .reasoning("Tracking agent executed " + phase + " phase")
                    .build();
        }
        
        public String getLastExecutionId() {
            return lastExecutionId;
        }
        
        public IntentResult getLastIntent() {
            return lastIntent;
        }
        
        public SDLCState getLastState() {
            return lastState;
        }
        
        public int getLastContextSize() {
            return lastContextSize;
        }
        
        public List<AgentResult> getLastPreviousResults() {
            return lastPreviousResults;
        }
    }
    
    /**
     * Agent that produces data in its result for testing data flow.
     */
    private static class DataProducingAgent implements Agent {
        private final String type;
        private final String dataKey;
        private final String dataValue;
        
        public DataProducingAgent(String type, String dataKey, String dataValue) {
            this.type = type;
            this.dataKey = dataKey;
            this.dataValue = dataValue;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public AgentResult plan(AgentContext context) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(AgentPhase.PLAN)
                    .status(AgentStatus.SUCCESS)
                    .data(dataKey, dataValue)
                    .reasoning("Data producing agent")
                    .build();
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
            return createSkippedResult(AgentPhase.REFLECT);
        }
        
        @Override
        public boolean canHandle(AgentContext context) {
            return true;
        }
        
        private AgentResult createSkippedResult(AgentPhase phase) {
            return AgentResult.builder()
                    .agentType(type)
                    .phase(phase)
                    .status(AgentStatus.SUCCESS)
                    .reasoning("Skipped")
                    .build();
        }
    }
}
