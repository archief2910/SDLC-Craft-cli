package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.memory.CodebaseIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ExecutorAgent executes planned actions using LLM to analyze code and generate responses.
 * 
 * The executor uses AI to perform actual analysis of the codebase and provide real insights.
 * 
 * Design rationale:
 * - Uses LLM to analyze code and provide intelligent responses
 * - Scans actual files in the project
 * - Provides real, actionable insights
 */
@Component
public class ExecutorAgent implements Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutorAgent.class);
    
    private static final String AGENT_TYPE = "executor";
    private static final int MAX_RETRIES = 3;
    private static final String PROJECT_ROOT = "C:\\Users\\archi\\SDLCraft-CLI";
    
    private final LLMProvider llmProvider;
    private final CodebaseIndexer codebaseIndexer;
    
    public ExecutorAgent(LLMProvider llmProvider, CodebaseIndexer codebaseIndexer) {
        this.llmProvider = llmProvider;
        this.codebaseIndexer = codebaseIndexer;
    }
    
    @Override
    public String getType() {
        return AGENT_TYPE;
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        // Executor doesn't create plans
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.PLAN)
                .status(AgentStatus.SKIPPED)
                .reasoning("Executor agent does not create plans")
                .build();
    }
    
    @Override
    public AgentResult act(AgentContext context) {
        logger.info("Executing actions for intent: {}", context.getIntent().getIntent());
        
        try {
            // Get execution plan from planner result
            AgentResult plannerResult = context.getLastResultByType("planner");
            if (plannerResult == null || plannerResult.getData("plan") == null) {
                return AgentResult.builder()
                        .agentType(AGENT_TYPE)
                        .phase(AgentPhase.ACT)
                        .status(AgentStatus.FAILURE)
                        .error("No execution plan found")
                        .reasoning("Cannot execute without a plan from planner agent")
                        .build();
            }
            
            PlannerAgent.ExecutionPlan plan = (PlannerAgent.ExecutionPlan) plannerResult.getData("plan");
            
            // Execute each step in the plan
            List<StepResult> stepResults = new ArrayList<>();
            Map<String, Object> executionData = new HashMap<>();
            
            for (PlannerAgent.ExecutionStep step : plan.getSteps()) {
                logger.debug("Executing step: {}", step.getId());
                
                StepResult stepResult = executeStep(step, executionData, context);
                stepResults.add(stepResult);
                
                if (!stepResult.isSuccess()) {
                    // Step failed, stop execution
                    logger.warn("Step {} failed: {}", step.getId(), stepResult.getError());
                    
                    return AgentResult.builder()
                            .agentType(AGENT_TYPE)
                            .phase(AgentPhase.ACT)
                            .status(AgentStatus.FAILURE)
                            .data("stepResults", stepResults)
                            .data("failedStep", step.getId())
                            .error("Execution failed at step: " + step.getId())
                            .reasoning("Step '" + step.getId() + "' failed: " + stepResult.getError())
                            .build();
                }
                
                // Store step output for subsequent steps
                if (stepResult.getOutput() != null) {
                    executionData.put(step.getId(), stepResult.getOutput());
                }
            }
            
            // All steps succeeded
            String reasoning = buildExecutionReasoning(stepResults);
            
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.SUCCESS)
                    .data("stepResults", stepResults)
                    .data("executionData", executionData)
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Execution failed", e);
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("Execution failed: " + e.getMessage())
                    .reasoning("Exception occurred during execution: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public AgentResult observe(AgentContext context) {
        // Executor doesn't validate results
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.OBSERVE)
                .status(AgentStatus.SKIPPED)
                .reasoning("Executor agent does not validate results")
                .build();
    }
    
    @Override
    public AgentResult reflect(AgentContext context) {
        // Executor doesn't reflect on outcomes
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Executor agent does not reflect on outcomes")
                .build();
    }
    
    @Override
    public boolean canHandle(AgentContext context) {
        // Executor can handle any context that has a plan
        AgentResult plannerResult = context.getLastResultByType("planner");
        return plannerResult != null && plannerResult.getData("plan") != null;
    }
    
    /**
     * Execute a single step from the plan.
     */
    private StepResult executeStep(PlannerAgent.ExecutionStep step, Map<String, Object> executionData,
                                   AgentContext context) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                // Check dependencies
                for (String dependency : step.getDependencies()) {
                    if (!executionData.containsKey(dependency)) {
                        return new StepResult(
                                step.getId(),
                                false,
                                null,
                                "Dependency not met: " + dependency
                        );
                    }
                }
                
                // Execute step based on action type
                Object output = executeAction(step, executionData, context);
                
                return new StepResult(step.getId(), true, output, null);
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt < MAX_RETRIES) {
                    logger.warn("Step {} failed (attempt {}/{}), retrying...", 
                               step.getId(), attempt, MAX_RETRIES);
                    
                    // Exponential backoff
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retries exhausted
        return new StepResult(
                step.getId(),
                false,
                null,
                "Failed after " + MAX_RETRIES + " attempts: " + 
                (lastException != null ? lastException.getMessage() : "Unknown error")
        );
    }
    
    /**
     * Execute the actual action for a step.
     */
    private Object executeAction(PlannerAgent.ExecutionStep step, Map<String, Object> executionData,
                                 AgentContext context) {
        String stepId = step.getId();
        String action = step.getAction();
        
        logger.info("Executing action: {}", action);
        
        try {
            // Gather codebase context with actual code samples
            String codebaseContext = gatherCodebaseContext(context);
            
            // Use LLM to analyze code and provide specific suggestions
            String analysisPrompt = buildAnalysisPrompt(action, codebaseContext, context);
            
            logger.debug("Calling LLM for code analysis...");
            String llmResponse = llmProvider.complete(analysisPrompt, new HashMap<>());
            logger.debug("LLM analysis complete");
            
            // Return structured result with LLM analysis
            Map<String, Object> result = new HashMap<>();
            result.put("action", action);
            result.put("codebaseContext", codebaseContext);
            result.put("analysis", llmResponse);
            result.put("completed", true);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to execute action: {}", action, e);
            Map<String, Object> result = new HashMap<>();
            result.put("action", action);
            result.put("error", e.getMessage());
            result.put("completed", false);
            return result;
        }
    }
    
    /**
     * Build prompt for LLM to analyze code and provide suggestions.
     */
    private String buildAnalysisPrompt(String action, String codebaseContext, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a senior software engineer analyzing a codebase for refactoring opportunities.\n\n");
        prompt.append("ACTION: ").append(action).append("\n\n");
        prompt.append("INTENT: ").append(context.getIntent().getIntent()).append("\n");
        prompt.append("TARGET: ").append(context.getIntent().getTarget()).append("\n\n");
        prompt.append("CODEBASE CONTEXT:\n").append(codebaseContext).append("\n\n");
        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Analyze the codebase structure and identify specific refactoring opportunities\n");
        prompt.append("2. Provide concrete code examples showing BEFORE and AFTER\n");
        prompt.append("3. Focus on: code smells, duplication, complexity, naming, structure\n");
        prompt.append("4. Prioritize high-impact refactorings\n");
        prompt.append("5. Be specific - mention exact file names, class names, method names\n\n");
        prompt.append("Provide your analysis with specific, actionable refactoring suggestions:");
        
        return prompt.toString();
    }
    
    /**
     * Gather codebase context using RAG - retrieves only relevant code based on query.
     */
    private String gatherCodebaseContext(AgentContext context) {
        StringBuilder contextBuilder = new StringBuilder();
        
        try {
            // Build query from intent and target
            String query = buildRAGQuery(context);
            
            contextBuilder.append("=== RAG-RETRIEVED RELEVANT CODE ===\n");
            contextBuilder.append("Query: ").append(query).append("\n\n");
            
            // Retrieve relevant code chunks using RAG
            List<CodebaseIndexer.CodeChunk> relevantChunks = codebaseIndexer.retrieveRelevantCode(query, 5);
            
            if (relevantChunks.isEmpty()) {
                contextBuilder.append("No relevant code found. Indexing codebase...\n");
                // Index codebase if not already indexed
                codebaseIndexer.indexCodebase(PROJECT_ROOT);
                // Try again
                relevantChunks = codebaseIndexer.retrieveRelevantCode(query, 5);
            }
            
            for (CodebaseIndexer.CodeChunk chunk : relevantChunks) {
                contextBuilder.append("\n--- File: ").append(chunk.getFilePath()).append(" ---\n");
                contextBuilder.append("Lines: ").append(chunk.getStartLine()).append("-").append(chunk.getEndLine()).append("\n");
                contextBuilder.append("Similarity: ").append(String.format("%.2f", chunk.getSimilarityScore())).append("\n");
                contextBuilder.append("```").append(chunk.getLanguage()).append("\n");
                contextBuilder.append(chunk.getContent());
                contextBuilder.append("```\n");
            }
            
            // Also provide file statistics
            contextBuilder.append("\n=== CODEBASE STATISTICS ===\n");
            List<String> javaFiles = findFiles(PROJECT_ROOT + "\\backend\\src\\main\\java", ".java");
            List<String> goFiles = findFiles(PROJECT_ROOT + "\\cli", ".go");
            contextBuilder.append("Total Java files: ").append(javaFiles.size()).append("\n");
            contextBuilder.append("Total Go files: ").append(goFiles.size()).append("\n");
            
        } catch (Exception e) {
            logger.error("Failed to gather codebase context", e);
            contextBuilder.append("Error gathering context: ").append(e.getMessage()).append("\n");
        }
        
        return contextBuilder.toString();
    }
    
    /**
     * Build RAG query from context.
     */
    private String buildRAGQuery(AgentContext context) {
        StringBuilder query = new StringBuilder();
        
        query.append(context.getIntent().getIntent()).append(" ");
        query.append(context.getIntent().getTarget()).append(" ");
        
        // Add modifiers if present
        if (context.getIntent().getModifiers() != null) {
            for (Map.Entry<String, String> entry : context.getIntent().getModifiers().entrySet()) {
                query.append(entry.getValue()).append(" ");
            }
        }
        
        return query.toString().trim();
    }
    
    /**
     * Find files with given extension in directory.
     */
    private List<String> findFiles(String directory, String extension) {
        List<String> files = new ArrayList<>();
        try {
            Path startPath = Paths.get(directory);
            if (!Files.exists(startPath)) {
                return files;
            }
            
            try (Stream<Path> paths = Files.walk(startPath)) {
                files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .map(Path::toString)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to find files in {}", directory, e);
        }
        return files;
    }
    
    /**
     * Read file content.
     */
    private String readFileContent(String filePath) {
        try {
            return Files.readString(Paths.get(filePath));
        } catch (Exception e) {
            logger.error("Failed to read file: {}", filePath, e);
            return "Error reading file: " + e.getMessage();
        }
    }
    
    /**
     * Build reasoning explanation for execution.
     */
    private String buildExecutionReasoning(List<StepResult> stepResults) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Successfully executed ").append(stepResults.size()).append(" steps: ");
        
        for (int i = 0; i < stepResults.size(); i++) {
            if (i > 0) {
                reasoning.append(", ");
            }
            reasoning.append(stepResults.get(i).getStepId());
        }
        
        reasoning.append(". All steps completed without errors.");
        
        return reasoning.toString();
    }
    
    /**
     * Result of executing a single step.
     */
    public static class StepResult {
        private final String stepId;
        private final boolean success;
        private final Object output;
        private final String error;
        
        public StepResult(String stepId, boolean success, Object output, String error) {
            this.stepId = stepId;
            this.success = success;
            this.output = output;
            this.error = error;
        }
        
        public String getStepId() {
            return stepId;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public Object getOutput() {
            return output;
        }
        
        public String getError() {
            return error;
        }
    }
}
