package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.*;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic AI Agent Handler - handles ANY command using AI with full agent orchestration.
 * This handler uses the PLAN → ACT → OBSERVE → REFLECT cycle to execute tasks autonomously.
 */
@Component
public class GenericAIAgentHandler implements IntentHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GenericAIAgentHandler.class);
    
    private final LLMProvider llmProvider;
    private final AgentOrchestrator orchestrator;
    
    public GenericAIAgentHandler(LLMProvider llmProvider, AgentOrchestrator orchestrator) {
        this.llmProvider = llmProvider;
        this.orchestrator = orchestrator;
    }
    
    @Override
    public String getIntentName() {
        return "*"; // Wildcard - handles all intents
    }
    
    @Override
    public boolean canHandle(IntentResult intent, SDLCState state) {
        // This is a catch-all handler - it can handle anything
        // But only if LLM is available
        return llmProvider.isAvailable();
    }
    
    @Override
    public AgentResult handle(AgentContext context) {
        logger.info("Handling generic AI request with full agent orchestration: {}", context.getIntent().getIntent());
        
        try {
            // Use the agent orchestrator to execute the full PLAN → ACT → OBSERVE → REFLECT cycle
            ExecutionResult result = orchestrator.execute(
                    context.getIntent(),
                    context.getCurrentState(),
                    context.getUserId(),
                    context.getProjectId()
            );
            
            // Convert ExecutionResult to AgentResult
            StringBuilder summary = new StringBuilder();
            summary.append(result.getSummary()).append("\n\n");
            
            // Add details from each phase
            for (AgentResult phaseResult : result.getAgentResults()) {
                summary.append(phaseResult.getPhase()).append(" (")
                       .append(phaseResult.getAgentType()).append("): ")
                       .append(phaseResult.getReasoning()).append("\n");
            }
            
            return AgentResult.builder()
                    .agentType("generic-ai-agent")
                    .phase(AgentPhase.REFLECT)
                    .status(result.getOverallStatus())
                    .data("summary", summary.toString())
                    .data("executionId", result.getExecutionId())
                    .data("durationMs", result.getDurationMs())
                    .data("agentResults", result.getAgentResults())
                    .reasoning(result.getSummary())
                    .build();
                    
        } catch (Exception e) {
            logger.error("Failed to handle generic AI request", e);
            return AgentResult.builder()
                    .agentType("generic-ai-agent")
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("AI agent failed: " + e.getMessage())
                    .reasoning("Exception occurred during AI processing")
                    .build();
        }
    }
    
    @Override
    public String getHelpText() {
        return "Generic AI agent that can handle any natural language request using full agent orchestration (PLAN → ACT → OBSERVE → REFLECT).";
    }
}
