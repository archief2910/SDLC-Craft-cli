package com.example.sdlcraft.custom;

import com.sdlcraft.backend.handler.IntentHandler;
import com.sdlcraft.backend.intent.IntentRequest;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.agent.AgentOrchestrator;
import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.ExecutionResult;
import com.sdlcraft.backend.agent.ExecutionStatus;
import com.sdlcraft.backend.sdlc.SDLCStateMachine;
import com.sdlcraft.backend.sdlc.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for the custom 'deploy' intent.
 * 
 * This handler coordinates deployment operations through the agent orchestrator,
 * ensuring proper validation and state management.
 */
@Component
public class DeployIntentHandler implements IntentHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DeployIntentHandler.class);
    
    private final AgentOrchestrator orchestrator;
    private final SDLCStateMachine stateMachine;
    
    public DeployIntentHandler(
            AgentOrchestrator orchestrator,
            SDLCStateMachine stateMachine) {
        this.orchestrator = orchestrator;
        this.stateMachine = stateMachine;
    }
    
    @Override
    public String getIntentName() {
        return "deploy";
    }
    
    @Override
    public ExecutionResult handle(IntentRequest request, IntentResult intent) {
        logger.info("Handling deploy intent for environment: {}", intent.getTarget());
        
        // Extract parameters
        String environment = intent.getTarget();
        String version = intent.getModifiers().getOrDefault("version", "latest");
        boolean rollback = Boolean.parseBoolean(
            intent.getModifiers().getOrDefault("rollback", "false")
        );
        boolean dryRun = Boolean.parseBoolean(
            intent.getModifiers().getOrDefault("dry-run", "false")
        );
        
        // Validate environment
        if (!isValidEnvironment(environment)) {
            return ExecutionResult.builder()
                .executionId(generateExecutionId())
                .status(ExecutionStatus.FAILED)
                .error("Invalid environment: " + environment)
                .message("Valid environments are: development, staging, production")
                .build();
        }
        
        // Check current SDLC state
        String projectId = request.getProjectId();
        Phase currentPhase = stateMachine.getCurrentState(projectId).getCurrentPhase();
        
        if (!canDeployFromPhase(currentPhase, environment)) {
            return ExecutionResult.builder()
                .executionId(generateExecutionId())
                .status(ExecutionStatus.FAILED)
                .error("Cannot deploy to " + environment + " from phase " + currentPhase)
                .message("Current phase must be TESTING or higher to deploy to " + environment)
                .build();
        }
        
        // Create agent context
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("environment", environment);
        parameters.put("version", version);
        parameters.put("rollback", rollback);
        parameters.put("dryRun", dryRun);
        parameters.put("projectId", projectId);
        parameters.put("userId", request.getUserId());
        
        AgentContext context = AgentContext.builder()
            .executionId(generateExecutionId())
            .intent(intent.getIntent())
            .currentState(stateMachine.getCurrentState(projectId))
            .parameters(parameters)
            .deadline(LocalDateTime.now().plusMinutes(30))
            .build();
        
        // Execute through agent orchestrator
        logger.info("Executing deployment through agent orchestrator");
        ExecutionResult result = orchestrator.execute(context);
        
        // Update SDLC state if deployment was successful
        if (result.getStatus() == ExecutionStatus.SUCCESS && !dryRun) {
            updateStateAfterDeployment(projectId, environment);
        }
        
        return result;
    }
    
    @Override
    public boolean supports(String intent) {
        return "deploy".equals(intent);
    }
    
    private boolean isValidEnvironment(String environment) {
        return environment != null && 
               (environment.equals("development") || 
                environment.equals("staging") || 
                environment.equals("production"));
    }
    
    private boolean canDeployFromPhase(Phase currentPhase, String environment) {
        // Development can be deployed from any phase
        if ("development".equals(environment)) {
            return true;
        }
        
        // Staging requires at least TESTING phase
        if ("staging".equals(environment)) {
            return currentPhase == Phase.TESTING || 
                   currentPhase == Phase.STAGING || 
                   currentPhase == Phase.PRODUCTION;
        }
        
        // Production requires STAGING phase
        if ("production".equals(environment)) {
            return currentPhase == Phase.STAGING || 
                   currentPhase == Phase.PRODUCTION;
        }
        
        return false;
    }
    
    private void updateStateAfterDeployment(String projectId, String environment) {
        try {
            if ("staging".equals(environment)) {
                stateMachine.transitionTo(projectId, Phase.STAGING);
            } else if ("production".equals(environment)) {
                stateMachine.transitionTo(projectId, Phase.PRODUCTION);
            }
            logger.info("Updated SDLC state after deployment to {}", environment);
        } catch (Exception e) {
            logger.error("Failed to update SDLC state after deployment", e);
        }
    }
    
    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString();
    }
}
