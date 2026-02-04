package com.sdlcraft.backend.workflow;

import com.sdlcraft.backend.integration.*;
import com.sdlcraft.backend.integration.jira.JiraIntegration;
import com.sdlcraft.backend.integration.bitbucket.BitbucketIntegration;
import com.sdlcraft.backend.integration.aws.AWSIntegration;
import com.sdlcraft.backend.integration.docker.DockerIntegration;
import com.sdlcraft.backend.integration.qa.QAIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes workflows following the micro-agent iterative pattern.
 * 
 * Key features:
 * - Iterative execution with retry logic
 * - Integration orchestration
 * - Context passing between steps
 * - Failure handling and recovery
 */
@Service
public class WorkflowExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutor.class);
    
    private final Map<String, Integration> integrations = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    public WorkflowExecutor(
        JiraIntegration jiraIntegration,
        BitbucketIntegration bitbucketIntegration,
        AWSIntegration awsIntegration,
        DockerIntegration dockerIntegration,
        QAIntegration qaIntegration
    ) {
        registerIntegration(jiraIntegration);
        registerIntegration(bitbucketIntegration);
        registerIntegration(awsIntegration);
        registerIntegration(dockerIntegration);
        registerIntegration(qaIntegration);
    }
    
    public void registerIntegration(Integration integration) {
        integrations.put(integration.getId(), integration);
        logger.info("Registered integration: {} (configured: {})", 
            integration.getId(), integration.isConfigured());
    }
    
    /**
     * Execute a workflow synchronously.
     */
    public WorkflowResult execute(Workflow workflow, Map<String, Object> context) {
        logger.info("Starting workflow: {}", workflow.name());
        LocalDateTime startTime = LocalDateTime.now();
        
        WorkflowContext ctx = new WorkflowContext(context);
        List<StepResult> stepResults = new ArrayList<>();
        boolean success = true;
        
        for (WorkflowStep step : workflow.steps()) {
            logger.info("Executing step: {} ({})", step.name(), step.id());
            
            // Check condition
            if (step.condition() != null && !evaluateCondition(step.condition(), ctx)) {
                logger.info("Skipping step {} due to condition", step.id());
                stepResults.add(new StepResult(step.id(), step.name(), true, "Skipped (condition false)", Map.of()));
                continue;
            }
            
            // Execute with retries
            StepResult result = executeStepWithRetry(step, ctx);
            stepResults.add(result);
            
            // Update context with result
            ctx.put("step_" + step.id() + "_result", result);
            ctx.put("lastStepSuccess", result.success());
            
            if (!result.success() && !step.continueOnFailure()) {
                logger.error("Workflow failed at step: {}", step.id());
                success = false;
                break;
            }
        }
        
        return new WorkflowResult(
            workflow.id(),
            success,
            stepResults,
            startTime,
            LocalDateTime.now(),
            ctx.getAll()
        );
    }
    
    /**
     * Execute a workflow asynchronously.
     */
    public CompletableFuture<WorkflowResult> executeAsync(Workflow workflow, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> execute(workflow, context), executorService);
    }
    
    /**
     * Execute step with retry logic.
     */
    private StepResult executeStepWithRetry(WorkflowStep step, WorkflowContext ctx) {
        int attempts = 0;
        int maxAttempts = step.maxRetries() + 1;
        StepResult lastResult = null;
        
        while (attempts < maxAttempts) {
            attempts++;
            
            try {
                lastResult = executeStep(step, ctx);
                
                if (lastResult.success()) {
                    return lastResult;
                }
                
                if (attempts < maxAttempts) {
                    logger.warn("Step {} failed, retrying ({}/{})", step.id(), attempts, maxAttempts);
                    Thread.sleep(1000 * attempts); // Exponential backoff
                }
            } catch (Exception e) {
                logger.error("Step {} threw exception", step.id(), e);
                lastResult = new StepResult(step.id(), step.name(), false, e.getMessage(), Map.of());
                
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(1000 * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return lastResult;
    }
    
    /**
     * Execute a single step.
     */
    private StepResult executeStep(WorkflowStep step, WorkflowContext ctx) {
        Integration integration = integrations.get(step.integrationId());
        
        if (integration == null) {
            return new StepResult(step.id(), step.name(), false, 
                "Integration not found: " + step.integrationId(), Map.of());
        }
        
        if (!integration.isConfigured()) {
            return new StepResult(step.id(), step.name(), false,
                "Integration not configured: " + step.integrationId(), Map.of());
        }
        
        try {
            // Resolve parameters from context
            Map<String, Object> resolvedParams = resolveParameters(step.parameters(), ctx);
            
            // Find and invoke the action method
            IntegrationResult result = invokeAction(integration, step.action(), resolvedParams);
            
            // Store result in context for subsequent steps
            ctx.put(step.id() + "_output", result.data());
            
            return new StepResult(
                step.id(),
                step.name(),
                result.success(),
                result.message(),
                result.data()
            );
        } catch (Exception e) {
            logger.error("Failed to execute step {}", step.id(), e);
            return new StepResult(step.id(), step.name(), false, e.getMessage(), Map.of());
        }
    }
    
    /**
     * Invoke an action on an integration using reflection.
     */
    private IntegrationResult invokeAction(Integration integration, String action, Map<String, Object> params) {
        try {
            // Find method with matching name
            Method[] methods = integration.getClass().getMethods();
            for (Method method : methods) {
                if (method.getName().equals(action)) {
                    // Build arguments
                    Object[] args = buildArguments(method, params);
                    Object result = method.invoke(integration, args);
                    
                    if (result instanceof IntegrationResult) {
                        return (IntegrationResult) result;
                    }
                    
                    // Wrap non-IntegrationResult returns
                    return IntegrationResult.success(
                        integration.getId(),
                        action,
                        "Action completed",
                        Map.of("result", result != null ? result : "void"),
                        0
                    );
                }
            }
            
            return IntegrationResult.failure(integration.getId(), action, 
                "Action not found: " + action);
        } catch (Exception e) {
            logger.error("Failed to invoke action {} on {}", action, integration.getId(), e);
            return IntegrationResult.failure(integration.getId(), action, e.getMessage());
        }
    }
    
    /**
     * Build method arguments from parameters.
     */
    private Object[] buildArguments(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        
        // Simple positional mapping for now
        // In a full implementation, use parameter annotations
        List<Object> values = new ArrayList<>(params.values());
        for (int i = 0; i < paramTypes.length && i < values.size(); i++) {
            args[i] = convertType(values.get(i), paramTypes[i]);
        }
        
        return args;
    }
    
    /**
     * Convert value to target type.
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        
        // Handle basic conversions
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        
        return value;
    }
    
    /**
     * Resolve parameter placeholders from context.
     * Supports ${variable} syntax.
     */
    private Map<String, Object> resolveParameters(Map<String, Object> params, WorkflowContext ctx) {
        Map<String, Object> resolved = new HashMap<>();
        
        for (var entry : params.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof String strValue) {
                // Replace ${var} placeholders
                String resolved_value = strValue;
                for (var ctxEntry : ctx.getAll().entrySet()) {
                    resolved_value = resolved_value.replace(
                        "${" + ctxEntry.getKey() + "}", 
                        ctxEntry.getValue() != null ? ctxEntry.getValue().toString() : ""
                    );
                }
                resolved.put(entry.getKey(), resolved_value);
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        
        return resolved;
    }
    
    /**
     * Evaluate a condition expression.
     */
    private boolean evaluateCondition(String condition, WorkflowContext ctx) {
        // Simple condition evaluation
        // Full implementation would use SpEL or similar
        if (condition.equals("${lastStepSuccess}")) {
            return Boolean.TRUE.equals(ctx.get("lastStepSuccess"));
        }
        if (condition.startsWith("${") && condition.endsWith("}")) {
            String var = condition.substring(2, condition.length() - 1);
            return ctx.get(var) != null && !Boolean.FALSE.equals(ctx.get(var));
        }
        return true;
    }
    
    /**
     * Get all registered integrations.
     */
    public Map<String, IntegrationHealth> getIntegrationHealth() {
        Map<String, IntegrationHealth> health = new HashMap<>();
        for (var entry : integrations.entrySet()) {
            health.put(entry.getKey(), entry.getValue().healthCheck());
        }
        return health;
    }
    
    /**
     * Workflow execution context.
     */
    private static class WorkflowContext {
        private final Map<String, Object> data = new ConcurrentHashMap<>();
        
        WorkflowContext(Map<String, Object> initial) {
            if (initial != null) {
                data.putAll(initial);
            }
        }
        
        void put(String key, Object value) {
            data.put(key, value);
        }
        
        Object get(String key) {
            return data.get(key);
        }
        
        Map<String, Object> getAll() {
            return new HashMap<>(data);
        }
    }
}

