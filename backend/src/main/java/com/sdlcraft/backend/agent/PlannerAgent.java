package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PlannerAgent breaks down high-level intents into concrete execution steps using LLM.
 * 
 * The planner uses AI to analyze the intent and create a detailed execution plan.
 * 
 * Design rationale:
 * - Uses LLM to generate intelligent, context-aware plans
 * - Adapts planning strategy based on intent and current state
 * - Provides reasoning for each step
 */
@Component
public class PlannerAgent implements Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(PlannerAgent.class);
    
    private static final String AGENT_TYPE = "planner";
    private final LLMProvider llmProvider;
    
    public PlannerAgent(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }
    
    @Override
    public String getType() {
        return AGENT_TYPE;
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        logger.info("Planning execution for intent: {}", context.getIntent().getIntent());
        
        try {
            IntentResult intent = context.getIntent();
            SDLCState state = context.getCurrentState();
            
            // Use LLM to generate execution plan
            String prompt = buildPlanningPrompt(intent, state, context);
            String llmResponse = llmProvider.complete(prompt, new HashMap<>());
            
            // Parse LLM response into execution plan
            ExecutionPlan executionPlan = parseLLMPlan(llmResponse, intent);
            
            // Estimate execution time and risk
            long estimatedDurationMs = estimateExecutionTime(executionPlan);
            RiskLevel estimatedRisk = estimateRisk(executionPlan, state);
            
            // Build reasoning
            String reasoning = "AI-generated plan: " + llmResponse;
            
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.PLAN)
                    .status(AgentStatus.SUCCESS)
                    .data("plan", executionPlan)
                    .data("llmResponse", llmResponse)
                    .data("estimatedDurationMs", estimatedDurationMs)
                    .data("estimatedRisk", estimatedRisk)
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Planning failed", e);
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.PLAN)
                    .status(AgentStatus.FAILURE)
                    .error("Planning failed: " + e.getMessage())
                    .reasoning("Unable to create execution plan due to: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Build prompt for LLM to generate execution plan.
     */
    private String buildPlanningPrompt(IntentResult intent, SDLCState state, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an AI agent planning how to execute a user's request.\n\n");
        prompt.append("USER REQUEST:\n");
        prompt.append("Intent: ").append(intent.getIntent()).append("\n");
        if (intent.getTarget() != null) {
            prompt.append("Target: ").append(intent.getTarget()).append("\n");
        }
        prompt.append("Original command: ").append(intent.getIntent()).append(" ").append(intent.getTarget() != null ? intent.getTarget() : "").append("\n\n");
        
        prompt.append("CURRENT PROJECT STATE:\n");
        if (state != null) {
            prompt.append("Phase: ").append(state.getCurrentPhase()).append("\n");
            prompt.append("Risk Level: ").append(state.getRiskLevel()).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("PROJECT CONTEXT:\n");
        prompt.append("This is a Java Spring Boot backend + Go CLI project called SDLCraft.\n");
        prompt.append("Project root: C:\\Users\\archi\\SDLCraft-CLI\n");
        prompt.append("Backend: backend/src/main/java/com/sdlcraft/backend/\n");
        prompt.append("CLI: cli/\n\n");
        
        prompt.append("YOUR TASK:\n");
        prompt.append("Generate a detailed execution plan with specific, actionable steps.\n");
        prompt.append("Each step should describe EXACTLY what needs to be done.\n");
        prompt.append("Be specific about file paths, commands, and actions.\n\n");
        
        prompt.append("RESPONSE FORMAT:\n");
        prompt.append("Provide a numbered list of steps, each on a new line:\n");
        prompt.append("1. [Step description]\n");
        prompt.append("2. [Step description]\n");
        prompt.append("etc.\n\n");
        
        prompt.append("EXECUTION PLAN:\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse LLM response into execution plan.
     */
    private ExecutionPlan parseLLMPlan(String llmResponse, IntentResult intent) {
        ExecutionPlan plan = new ExecutionPlan();
        
        // Parse numbered steps from LLM response
        String[] lines = llmResponse.split("\n");
        int stepNumber = 1;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Match patterns like "1.", "1)", "Step 1:", etc.
            if (line.matches("^\\d+[.):)].*") || line.matches("^Step \\d+:.*")) {
                // Extract step description
                String description = line.replaceFirst("^\\d+[.):)]\\s*", "")
                                        .replaceFirst("^Step \\d+:\\s*", "")
                                        .trim();
                
                if (!description.isEmpty()) {
                    plan.addStep(new ExecutionStep(
                            "step-" + stepNumber,
                            description,
                            "AI-generated step",
                            Collections.emptyList()
                    ));
                    stepNumber++;
                }
            }
        }
        
        // If no steps were parsed, create a generic step
        if (plan.getSteps().isEmpty()) {
            plan.addStep(new ExecutionStep(
                    "execute-intent",
                    llmResponse,
                    "Execute " + intent.getIntent() + " intent",
                    Collections.emptyList()
            ));
        }
        
        return plan;
    }
    
    @Override
    public AgentResult act(AgentContext context) {
        // Planner doesn't execute actions
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.ACT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Planner agent does not execute actions")
                .build();
    }
    
    @Override
    public AgentResult observe(AgentContext context) {
        // Planner doesn't validate results
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.OBSERVE)
                .status(AgentStatus.SKIPPED)
                .reasoning("Planner agent does not validate results")
                .build();
    }
    
    @Override
    public AgentResult reflect(AgentContext context) {
        // Planner doesn't reflect on outcomes
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Planner agent does not reflect on outcomes")
                .build();
    }
    
    @Override
    public boolean canHandle(AgentContext context) {
        // Planner can handle any context
        return true;
    }
    
    /**
     * Estimate execution time based on plan complexity.
     */
    private long estimateExecutionTime(ExecutionPlan plan) {
        // Simple heuristic: 5 seconds per step
        return plan.getSteps().size() * 5000L;
    }
    
    /**
     * Estimate risk based on plan and current state.
     */
    private RiskLevel estimateRisk(ExecutionPlan plan, SDLCState state) {
        // Consider state risk and plan complexity
        int riskScore = 0;
        
        // Factor in current state risk
        if (state != null) {
            switch (state.getRiskLevel()) {
                case CRITICAL:
                    riskScore += 40;
                    break;
                case HIGH:
                    riskScore += 30;
                    break;
                case MEDIUM:
                    riskScore += 20;
                    break;
                case LOW:
                    riskScore += 10;
                    break;
            }
        }
        
        // Factor in plan complexity
        int stepCount = plan.getSteps().size();
        if (stepCount > 5) {
            riskScore += 20;
        } else if (stepCount > 3) {
            riskScore += 10;
        }
        
        return RiskLevel.fromScore(riskScore / 100.0);
    }
    
    /**
     * Execution plan containing ordered steps.
     */
    public static class ExecutionPlan {
        private final List<ExecutionStep> steps = new ArrayList<>();
        
        public void addStep(ExecutionStep step) {
            steps.add(step);
        }
        
        public List<ExecutionStep> getSteps() {
            return new ArrayList<>(steps);
        }
    }
    
    /**
     * Individual step in an execution plan.
     */
    public static class ExecutionStep {
        private final String id;
        private final String action;
        private final String reasoning;
        private final List<String> dependencies;
        
        public ExecutionStep(String id, String action, String reasoning, List<String> dependencies) {
            this.id = id;
            this.action = action;
            this.reasoning = reasoning;
            this.dependencies = new ArrayList<>(dependencies);
        }
        
        public String getId() {
            return id;
        }
        
        public String getAction() {
            return action;
        }
        
        public String getReasoning() {
            return reasoning;
        }
        
        public List<String> getDependencies() {
            return new ArrayList<>(dependencies);
        }
    }
}
