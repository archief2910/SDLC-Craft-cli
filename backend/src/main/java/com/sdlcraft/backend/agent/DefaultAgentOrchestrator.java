package com.sdlcraft.backend.agent;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Default implementation of AgentOrchestrator.
 * 
 * Coordinates multi-agent workflows following PLAN → ACT → OBSERVE → REFLECT pattern.
 * Manages agent registration, execution context, error handling, and async execution.
 * 
 * Design rationale:
 * - Uses ConcurrentHashMap for thread-safe agent registration
 * - ExecutorService enables async execution with proper resource management
 * - Timeout handling prevents runaway executions
 * - Reflection agent is invoked automatically on failures
 * - Complete execution traces are maintained for debugging
 */
@Service
public class DefaultAgentOrchestrator implements AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultAgentOrchestrator.class);
    
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final int THREAD_POOL_SIZE = 10;
    
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, ExecutionStatus> executions = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final AgentExecutionRepository executionRepository;
    
    public DefaultAgentOrchestrator(AgentExecutionRepository executionRepository) {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.executionRepository = executionRepository;
    }
    
    @Override
    public ExecutionResult execute(IntentResult intent, SDLCState state, String userId, String projectId) {
        String executionId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();
        
        logger.info("Starting execution {} for intent: {}", executionId, intent.getIntent());
        
        try {
            // Create initial context
            AgentContext context = new AgentContext.Builder()
                    .executionId(executionId)
                    .intent(intent)
                    .currentState(state)
                    .userId(userId)
                    .projectId(projectId)
                    .deadline(LocalDateTime.now().plusSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .build();
            
            // Execute PLAN → ACT → OBSERVE → REFLECT cycle
            context = executePlanPhase(context);
            context = executeActPhase(context);
            context = executeObservePhase(context);
            context = executeReflectPhase(context);
            
            // Build final result
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            
            AgentStatus overallStatus = determineOverallStatus(context.getPreviousResults());
            
            ExecutionResult result = ExecutionResult.builder()
                    .executionId(executionId)
                    .overallStatus(overallStatus)
                    .agentResults(context.getPreviousResults())
                    .summary(generateSummary(context))
                    .startTime(startTime)
                    .endTime(endTime)
                    .durationMs(durationMs)
                    .build();
            
            // Persist execution trace
            persistExecutionTrace(result, intent, userId, projectId);
            
            logger.info("Execution {} completed with status: {}", executionId, overallStatus);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Execution {} failed with exception", executionId, e);
            
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            
            return ExecutionResult.builder()
                    .executionId(executionId)
                    .overallStatus(AgentStatus.FAILURE)
                    .summary("Execution failed: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(endTime)
                    .durationMs(durationMs)
                    .build();
        }
    }
    
    @Override
    public String executeAsync(IntentResult intent, SDLCState state, String userId, String projectId,
                               ExecutionCallback callback) {
        String executionId = UUID.randomUUID().toString();
        
        // Update execution status
        executions.put(executionId, new ExecutionStatus(
                executionId,
                ExecutionStatus.ExecutionState.QUEUED,
                null,
                null,
                0,
                LocalDateTime.now(),
                "Execution queued"
        ));
        
        // Submit async execution
        executorService.submit(() -> {
            try {
                executions.put(executionId, new ExecutionStatus(
                        executionId,
                        ExecutionStatus.ExecutionState.RUNNING,
                        null,
                        AgentPhase.PLAN,
                        0,
                        LocalDateTime.now(),
                        "Execution started"
                ));
                
                ExecutionResult result = execute(intent, state, userId, projectId);
                
                executions.put(executionId, new ExecutionStatus(
                        executionId,
                        result.isSuccess() ? ExecutionStatus.ExecutionState.COMPLETED 
                                          : ExecutionStatus.ExecutionState.FAILED,
                        null,
                        AgentPhase.REFLECT,
                        100,
                        LocalDateTime.now(),
                        result.getSummary()
                ));
                
                callback.onComplete(result);
                
            } catch (Exception e) {
                logger.error("Async execution {} failed", executionId, e);
                
                executions.put(executionId, new ExecutionStatus(
                        executionId,
                        ExecutionStatus.ExecutionState.FAILED,
                        null,
                        null,
                        0,
                        LocalDateTime.now(),
                        "Execution failed: " + e.getMessage()
                ));
                
                callback.onError(e.getMessage(), e);
            }
        });
        
        return executionId;
    }
    
    @Override
    public void registerAgent(Agent agent) {
        agents.put(agent.getType(), agent);
        logger.info("Registered agent: {}", agent.getType());
    }
    
    @Override
    public void unregisterAgent(String agentType) {
        agents.remove(agentType);
        logger.info("Unregistered agent: {}", agentType);
    }
    
    @Override
    public List<Agent> getRegisteredAgents() {
        return new ArrayList<>(agents.values());
    }
    
    @Override
    public Agent getAgent(String agentType) {
        return agents.get(agentType);
    }
    
    @Override
    public boolean cancelExecution(String executionId) {
        ExecutionStatus status = executions.get(executionId);
        if (status != null && status.isRunning()) {
            executions.put(executionId, new ExecutionStatus(
                    executionId,
                    ExecutionStatus.ExecutionState.CANCELLED,
                    status.getCurrentAgent(),
                    status.getCurrentPhase(),
                    status.getPercentComplete(),
                    status.getStartTime(),
                    "Execution cancelled by user"
            ));
            return true;
        }
        return false;
    }
    
    @Override
    public ExecutionStatus getExecutionStatus(String executionId) {
        return executions.get(executionId);
    }
    
    /**
     * Execute PLAN phase: Find planner agent and create execution plan.
     */
    private AgentContext executePlanPhase(AgentContext context) {
        Agent planner = findAgentForPhase("planner", context);
        if (planner == null) {
            logger.warn("No planner agent found, skipping PLAN phase");
            return context;
        }
        
        logger.debug("Executing PLAN phase with agent: {}", planner.getType());
        
        try {
            AgentResult result = planner.plan(context);
            return context.withResult(result);
        } catch (Exception e) {
            logger.error("PLAN phase failed", e);
            AgentResult errorResult = AgentResult.builder()
                    .agentType(planner.getType())
                    .phase(AgentPhase.PLAN)
                    .status(AgentStatus.FAILURE)
                    .error("Planning failed: " + e.getMessage())
                    .reasoning("Exception occurred during planning")
                    .build();
            return context.withResult(errorResult);
        }
    }
    
    /**
     * Execute ACT phase: Find executor agent and execute planned actions.
     */
    private AgentContext executeActPhase(AgentContext context) {
        // Check if planning succeeded
        AgentResult planResult = context.getLastResultByType("planner");
        if (planResult != null && planResult.isFailure()) {
            logger.warn("Skipping ACT phase due to planning failure");
            return context;
        }
        
        Agent executor = findAgentForPhase("executor", context);
        if (executor == null) {
            logger.warn("No executor agent found, skipping ACT phase");
            return context;
        }
        
        logger.debug("Executing ACT phase with agent: {}", executor.getType());
        
        try {
            AgentResult result = executor.act(context);
            return context.withResult(result);
        } catch (Exception e) {
            logger.error("ACT phase failed", e);
            AgentResult errorResult = AgentResult.builder()
                    .agentType(executor.getType())
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("Execution failed: " + e.getMessage())
                    .reasoning("Exception occurred during execution")
                    .build();
            return context.withResult(errorResult);
        }
    }
    
    /**
     * Execute OBSERVE phase: Find validator agent and validate results.
     */
    private AgentContext executeObservePhase(AgentContext context) {
        // Check if execution succeeded
        AgentResult actResult = context.getLastResultByType("executor");
        if (actResult != null && actResult.isFailure()) {
            logger.warn("Skipping OBSERVE phase due to execution failure");
            return context;
        }
        
        Agent validator = findAgentForPhase("validator", context);
        if (validator == null) {
            logger.warn("No validator agent found, skipping OBSERVE phase");
            return context;
        }
        
        logger.debug("Executing OBSERVE phase with agent: {}", validator.getType());
        
        try {
            AgentResult result = validator.observe(context);
            return context.withResult(result);
        } catch (Exception e) {
            logger.error("OBSERVE phase failed", e);
            AgentResult errorResult = AgentResult.builder()
                    .agentType(validator.getType())
                    .phase(AgentPhase.OBSERVE)
                    .status(AgentStatus.FAILURE)
                    .error("Validation failed: " + e.getMessage())
                    .reasoning("Exception occurred during validation")
                    .build();
            return context.withResult(errorResult);
        }
    }
    
    /**
     * Execute REFLECT phase: Find reflection agent and analyze outcomes.
     * Always executed, especially important on failures.
     */
    private AgentContext executeReflectPhase(AgentContext context) {
        Agent reflector = findAgentForPhase("reflection", context);
        if (reflector == null) {
            logger.warn("No reflection agent found, skipping REFLECT phase");
            return context;
        }
        
        logger.debug("Executing REFLECT phase with agent: {}", reflector.getType());
        
        try {
            AgentResult result = reflector.reflect(context);
            return context.withResult(result);
        } catch (Exception e) {
            logger.error("REFLECT phase failed", e);
            AgentResult errorResult = AgentResult.builder()
                    .agentType(reflector.getType())
                    .phase(AgentPhase.REFLECT)
                    .status(AgentStatus.FAILURE)
                    .error("Reflection failed: " + e.getMessage())
                    .reasoning("Exception occurred during reflection")
                    .build();
            return context.withResult(errorResult);
        }
    }
    
    /**
     * Find an agent that can handle the given context.
     */
    private Agent findAgentForPhase(String preferredType, AgentContext context) {
        // First try to find agent by preferred type
        Agent agent = agents.get(preferredType);
        if (agent != null && agent.canHandle(context)) {
            return agent;
        }
        
        // Fall back to any agent that can handle the context
        for (Agent candidate : agents.values()) {
            if (candidate.canHandle(context)) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Determine overall execution status from agent results.
     */
    private AgentStatus determineOverallStatus(List<AgentResult> results) {
        if (results.isEmpty()) {
            return AgentStatus.FAILURE;
        }
        
        boolean hasFailure = false;
        boolean hasPartial = false;
        
        for (AgentResult result : results) {
            if (result.isFailure()) {
                hasFailure = true;
            } else if (result.isPartial()) {
                hasPartial = true;
            }
        }
        
        if (hasFailure) {
            return AgentStatus.FAILURE;
        } else if (hasPartial) {
            return AgentStatus.PARTIAL;
        } else {
            return AgentStatus.SUCCESS;
        }
    }
    
    /**
     * Generate execution summary from context.
     */
    private String generateSummary(AgentContext context) {
        List<AgentResult> results = context.getPreviousResults();
        if (results.isEmpty()) {
            return "No agents executed";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Executed ").append(results.size()).append(" agent phases: ");
        
        for (int i = 0; i < results.size(); i++) {
            AgentResult result = results.get(i);
            if (i > 0) {
                summary.append(" → ");
            }
            summary.append(result.getPhase()).append("(").append(result.getStatus()).append(")");
        }
        
        return summary.toString();
    }
    
    /**
     * Persist execution trace to database for debugging and learning.
     */
    private void persistExecutionTrace(ExecutionResult result, IntentResult intent, 
                                      String userId, String projectId) {
        try {
            AgentExecutionEntity entity = new AgentExecutionEntity();
            entity.setExecutionId(result.getExecutionId());
            entity.setUserId(userId);
            entity.setProjectId(projectId);
            entity.setIntent(intent.getIntent());
            entity.setTarget(intent.getTarget());
            entity.setModifiers(intent.getModifiers());
            entity.setOverallStatus(result.getOverallStatus());
            entity.setSummary(result.getSummary());
            entity.setStartTime(result.getStartTime());
            entity.setEndTime(result.getEndTime());
            entity.setDurationMs(result.getDurationMs());
            
            // Store agent results as JSON
            Map<String, Object> resultsMap = new HashMap<>();
            for (int i = 0; i < result.getAgentResults().size(); i++) {
                AgentResult agentResult = result.getAgentResults().get(i);
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("agentType", agentResult.getAgentType());
                resultData.put("phase", agentResult.getPhase().toString());
                resultData.put("status", agentResult.getStatus().toString());
                resultData.put("reasoning", agentResult.getReasoning());
                if (agentResult.getError() != null) {
                    resultData.put("error", agentResult.getError());
                }
                resultsMap.put("result_" + i, resultData);
            }
            entity.setAgentResults(resultsMap);
            
            executionRepository.save(entity);
            logger.debug("Persisted execution trace: {}", result.getExecutionId());
            
        } catch (Exception e) {
            // Don't fail execution if persistence fails
            logger.error("Failed to persist execution trace", e);
        }
    }
}
