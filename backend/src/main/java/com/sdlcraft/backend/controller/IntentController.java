package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.handler.IntentHandler;
import com.sdlcraft.backend.intent.IntentInferenceService;
import com.sdlcraft.backend.intent.IntentRequest;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;
import com.sdlcraft.backend.sdlc.SDLCStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/intent")
public class IntentController {
    
    private static final Logger logger = LoggerFactory.getLogger(IntentController.class);
    
    private final IntentInferenceService intentInferenceService;
    private final SDLCStateMachine stateMachine;
    private final List<IntentHandler> intentHandlers;
    
    public IntentController(IntentInferenceService intentInferenceService,
                          SDLCStateMachine stateMachine,
                          List<IntentHandler> intentHandlers) {
        this.intentInferenceService = intentInferenceService;
        this.stateMachine = stateMachine;
        this.intentHandlers = intentHandlers;
    }
    
    @PostMapping("/infer")
    public ResponseEntity<IntentResult> inferIntent(@RequestBody IntentRequest request) {
        try {
            IntentResult result = intentInferenceService.inferIntent(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to infer intent: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeIntent(@RequestBody IntentRequest request) {
        try {
            // Infer intent first
            IntentResult intent = intentInferenceService.inferIntent(request);
            
            // Get current state (initialize if doesn't exist)
            SDLCState state = getOrInitializeState(request.getProjectId());
            
            // Find handler for this intent
            // Try specific handlers first, then generic handler
            IntentHandler handler = intentHandlers.stream()
                    .filter(h -> !h.getIntentName().equals("*")) // Skip generic handler first
                    .filter(h -> h.canHandle(intent, state))
                    .findFirst()
                    .orElse(null);
            
            // If no specific handler found, try generic handler
            if (handler == null) {
                handler = intentHandlers.stream()
                        .filter(h -> h.getIntentName().equals("*")) // Generic handler
                        .filter(h -> h.canHandle(intent, state))
                        .findFirst()
                        .orElse(null);
            }
            
            if (handler == null) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "message", "No handler found for intent: " + intent.getIntent() + ". Please ensure an AI provider is configured."
                ));
            }
            
            // Create agent context using builder
            AgentContext context = new AgentContext.Builder()
                    .executionId(java.util.UUID.randomUUID().toString())
                    .intent(intent)
                    .currentState(state)
                    .userId(request.getUserId())
                    .projectId(request.getProjectId())
                    .parameters(request.getContext())
                    .build();
            
            // Execute handler
            AgentResult result = handler.handle(context);
            
            // Return result
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "intent", intent.getIntent(),
                    "target", intent.getTarget(),
                    "agentResult", Map.of(
                            "agentType", result.getAgentType(),
                            "phase", result.getPhase().toString(),
                            "status", result.getStatus().toString(),
                            "data", result.getData(),
                            "reasoning", result.getReasoning(),
                            "error", result.getError() != null ? result.getError() : ""
                    )
            ));
            
        } catch (Exception e) {
            logger.error("Failed to execute intent: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
    
    private SDLCState getOrInitializeState(String projectId) {
        try {
            return stateMachine.getCurrentState(projectId);
        } catch (Exception e) {
            return stateMachine.initializeProject(projectId);
        }
    }
}
