package com.sdlcraft.backend.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ReflectionAgent analyzes execution outcomes and suggests improvements.
 * 
 * The reflection agent compares actual vs expected results, identifies
 * failure root causes, and suggests recovery actions or plan adjustments.
 * This is especially important when execution fails.
 * 
 * Design rationale:
 * - Always executes, even (especially) on failures
 * - Provides actionable recovery suggestions
 * - Learns from execution patterns
 * - Supports continuous improvement
 */
@Component
public class ReflectionAgent implements Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectionAgent.class);
    
    private static final String AGENT_TYPE = "reflection";
    
    @Override
    public String getType() {
        return AGENT_TYPE;
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        // Reflection agent doesn't create plans
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.PLAN)
                .status(AgentStatus.SKIPPED)
                .reasoning("Reflection agent does not create plans")
                .build();
    }
    
    @Override
    public AgentResult act(AgentContext context) {
        // Reflection agent doesn't execute actions
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.ACT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Reflection agent does not execute actions")
                .build();
    }
    
    @Override
    public AgentResult observe(AgentContext context) {
        // Reflection agent doesn't validate results
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.OBSERVE)
                .status(AgentStatus.SKIPPED)
                .reasoning("Reflection agent does not validate results")
                .build();
    }
    
    @Override
    public AgentResult reflect(AgentContext context) {
        logger.info("Reflecting on execution for intent: {}", context.getIntent().getIntent());
        
        try {
            // Gather all previous results
            List<AgentResult> previousResults = context.getPreviousResults();
            
            if (previousResults.isEmpty()) {
                return AgentResult.builder()
                        .agentType(AGENT_TYPE)
                        .phase(AgentPhase.REFLECT)
                        .status(AgentStatus.SUCCESS)
                        .reasoning("No execution results to reflect on")
                        .build();
            }
            
            // Analyze execution outcome
            ExecutionAnalysis analysis = analyzeExecution(previousResults, context);
            
            // Generate insights and recommendations
            List<String> insights = generateInsights(analysis, context);
            List<String> recommendations = generateRecommendations(analysis, context);
            List<String> recoveryActions = generateRecoveryActions(analysis, context);
            
            // Build reasoning
            String reasoning = buildReflectionReasoning(analysis, insights, recommendations);
            
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.REFLECT)
                    .status(AgentStatus.SUCCESS)
                    .data("analysis", analysis)
                    .data("insights", insights)
                    .data("recommendations", recommendations)
                    .data("recoveryActions", recoveryActions)
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Reflection failed", e);
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.REFLECT)
                    .status(AgentStatus.FAILURE)
                    .error("Reflection failed: " + e.getMessage())
                    .reasoning("Exception occurred during reflection: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public boolean canHandle(AgentContext context) {
        // Reflection agent can always handle any context
        return true;
    }
    
    /**
     * Analyze execution results to understand what happened.
     */
    private ExecutionAnalysis analyzeExecution(List<AgentResult> results, AgentContext context) {
        ExecutionAnalysis analysis = new ExecutionAnalysis();
        
        // Count phase outcomes
        for (AgentResult result : results) {
            analysis.totalPhases++;
            
            if (result.isSuccess()) {
                analysis.successfulPhases++;
            } else if (result.isFailure()) {
                analysis.failedPhases++;
                analysis.failures.add(new FailureInfo(
                        result.getAgentType(),
                        result.getPhase(),
                        result.getError()
                ));
            } else if (result.isPartial()) {
                analysis.partialPhases++;
            }
        }
        
        // Determine overall outcome
        if (analysis.failedPhases > 0) {
            analysis.overallOutcome = "FAILURE";
        } else if (analysis.partialPhases > 0) {
            analysis.overallOutcome = "PARTIAL_SUCCESS";
        } else {
            analysis.overallOutcome = "SUCCESS";
        }
        
        // Identify failure patterns
        if (!analysis.failures.isEmpty()) {
            analysis.primaryFailure = analysis.failures.get(0);
            analysis.failurePhase = analysis.primaryFailure.getPhase();
        }
        
        return analysis;
    }
    
    /**
     * Generate insights from execution analysis.
     */
    private List<String> generateInsights(ExecutionAnalysis analysis, AgentContext context) {
        List<String> insights = new ArrayList<>();
        
        if ("SUCCESS".equals(analysis.overallOutcome)) {
            insights.add("Execution completed successfully through all phases");
            insights.add("All " + analysis.totalPhases + " agent phases executed without errors");
            
            // Check for efficiency insights
            if (analysis.totalPhases > 5) {
                insights.add("Execution involved " + analysis.totalPhases + 
                           " phases - consider optimizing for simpler workflows");
            }
            
        } else if ("PARTIAL_SUCCESS".equals(analysis.overallOutcome)) {
            insights.add("Execution completed with warnings or partial results");
            insights.add(analysis.successfulPhases + " of " + analysis.totalPhases + 
                       " phases succeeded, " + analysis.partialPhases + " had warnings");
            insights.add("Review warnings to ensure they are acceptable for your use case");
            
        } else {
            insights.add("Execution failed during " + analysis.failurePhase + " phase");
            insights.add("Failure occurred in " + analysis.primaryFailure.getAgentType() + " agent");
            
            // Analyze failure timing
            if (analysis.failurePhase == AgentPhase.PLAN) {
                insights.add("Early failure in planning suggests intent or context issues");
            } else if (analysis.failurePhase == AgentPhase.ACT) {
                insights.add("Failure during execution suggests environmental or resource issues");
            } else if (analysis.failurePhase == AgentPhase.OBSERVE) {
                insights.add("Validation failure suggests output did not meet expected criteria");
            }
        }
        
        return insights;
    }
    
    /**
     * Generate recommendations for future executions.
     */
    private List<String> generateRecommendations(ExecutionAnalysis analysis, AgentContext context) {
        List<String> recommendations = new ArrayList<>();
        
        if ("SUCCESS".equals(analysis.overallOutcome)) {
            recommendations.add("Execution pattern was successful - can be reused for similar intents");
            recommendations.add("Consider caching results if this is a frequently executed operation");
            
        } else if ("PARTIAL_SUCCESS".equals(analysis.overallOutcome)) {
            recommendations.add("Review validation findings to understand warnings");
            recommendations.add("Consider adjusting success criteria if warnings are acceptable");
            recommendations.add("Monitor for patterns if warnings occur frequently");
            
        } else {
            // Failure-specific recommendations
            if (analysis.failurePhase == AgentPhase.PLAN) {
                recommendations.add("Verify that the intent is correctly specified");
                recommendations.add("Check that required context and parameters are provided");
                recommendations.add("Ensure the current SDLC state supports this operation");
                
            } else if (analysis.failurePhase == AgentPhase.ACT) {
                recommendations.add("Verify that required resources are available");
                recommendations.add("Check network connectivity and service availability");
                recommendations.add("Review execution logs for detailed error information");
                recommendations.add("Consider retrying the operation after addressing the error");
                
            } else if (analysis.failurePhase == AgentPhase.OBSERVE) {
                recommendations.add("Review validation criteria to ensure they are appropriate");
                recommendations.add("Check if execution produced unexpected side effects");
                recommendations.add("Verify that the environment is in the expected state");
            }
        }
        
        return recommendations;
    }
    
    /**
     * Generate recovery actions for failures.
     */
    private List<String> generateRecoveryActions(ExecutionAnalysis analysis, AgentContext context) {
        List<String> actions = new ArrayList<>();
        
        if (!"FAILURE".equals(analysis.overallOutcome)) {
            return actions; // No recovery needed
        }
        
        FailureInfo failure = analysis.primaryFailure;
        
        // Generic recovery actions
        actions.add("Review error message: " + failure.getError());
        actions.add("Check system logs for additional context");
        
        // Phase-specific recovery actions
        if (failure.getPhase() == AgentPhase.PLAN) {
            actions.add("Verify intent syntax and parameters");
            actions.add("Check if target is valid for this intent");
            actions.add("Ensure project is in appropriate SDLC phase");
            
        } else if (failure.getPhase() == AgentPhase.ACT) {
            actions.add("Verify required services are running");
            actions.add("Check file system permissions");
            actions.add("Ensure sufficient resources (disk, memory, network)");
            actions.add("Retry operation after addressing the error");
            
        } else if (failure.getPhase() == AgentPhase.OBSERVE) {
            actions.add("Review validation findings for specific issues");
            actions.add("Check if execution produced partial results");
            actions.add("Verify environment state matches expectations");
        }
        
        // Intent-specific recovery actions
        String intent = context.getIntent().getIntent();
        if ("release".equals(intent)) {
            actions.add("Verify release readiness score meets threshold");
            actions.add("Check that all tests are passing");
            actions.add("Ensure deployment target is accessible");
        } else if ("test".equals(intent)) {
            actions.add("Review failing test cases");
            actions.add("Check test environment configuration");
        }
        
        return actions;
    }
    
    /**
     * Build reasoning explanation for reflection.
     */
    private String buildReflectionReasoning(ExecutionAnalysis analysis, List<String> insights,
                                           List<String> recommendations) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Analyzed execution with outcome: ").append(analysis.overallOutcome).append(". ");
        
        reasoning.append("Executed ").append(analysis.totalPhases).append(" phases: ");
        reasoning.append(analysis.successfulPhases).append(" succeeded");
        
        if (analysis.failedPhases > 0) {
            reasoning.append(", ").append(analysis.failedPhases).append(" failed");
        }
        if (analysis.partialPhases > 0) {
            reasoning.append(", ").append(analysis.partialPhases).append(" partial");
        }
        
        reasoning.append(". ");
        
        reasoning.append("Generated ").append(insights.size()).append(" insights and ")
                .append(recommendations.size()).append(" recommendations for improvement.");
        
        return reasoning.toString();
    }
    
    /**
     * Analysis of execution results.
     */
    public static class ExecutionAnalysis {
        private int totalPhases = 0;
        private int successfulPhases = 0;
        private int failedPhases = 0;
        private int partialPhases = 0;
        private String overallOutcome;
        private List<FailureInfo> failures = new ArrayList<>();
        private FailureInfo primaryFailure;
        private AgentPhase failurePhase;
        
        public int getTotalPhases() {
            return totalPhases;
        }
        
        public int getSuccessfulPhases() {
            return successfulPhases;
        }
        
        public int getFailedPhases() {
            return failedPhases;
        }
        
        public int getPartialPhases() {
            return partialPhases;
        }
        
        public String getOverallOutcome() {
            return overallOutcome;
        }
        
        public List<FailureInfo> getFailures() {
            return failures;
        }
        
        public FailureInfo getPrimaryFailure() {
            return primaryFailure;
        }
        
        public AgentPhase getFailurePhase() {
            return failurePhase;
        }
    }
    
    /**
     * Information about a failure.
     */
    public static class FailureInfo {
        private final String agentType;
        private final AgentPhase phase;
        private final String error;
        
        public FailureInfo(String agentType, AgentPhase phase, String error) {
            this.agentType = agentType;
            this.phase = phase;
            this.error = error;
        }
        
        public String getAgentType() {
            return agentType;
        }
        
        public AgentPhase getPhase() {
            return phase;
        }
        
        public String getError() {
            return error;
        }
    }
}
