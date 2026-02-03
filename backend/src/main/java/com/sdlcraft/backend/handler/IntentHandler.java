package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;

/**
 * Interface for intent-specific handlers.
 * 
 * Intent handlers provide specialized logic for executing specific intents.
 * They work alongside agents to provide domain-specific functionality.
 * 
 * Design rationale:
 * - Separates intent-specific logic from generic agent framework
 * - Enables specialized handling for each intent type
 * - Supports extensibility for custom intents
 * - Provides structured response format
 */
public interface IntentHandler {
    
    /**
     * Get the intent name this handler supports.
     * 
     * @return intent name (e.g., "status", "analyze", "improve")
     */
    String getIntentName();
    
    /**
     * Check if this handler can handle the given intent and context.
     * 
     * @param intent the intent to handle
     * @param state current SDLC state
     * @return true if this handler can handle the intent
     */
    boolean canHandle(IntentResult intent, SDLCState state);
    
    /**
     * Handle the intent execution.
     * 
     * This method is called by agents during the ACT phase to perform
     * intent-specific operations.
     * 
     * @param context agent execution context
     * @return result of intent handling
     */
    AgentResult handle(AgentContext context);
    
    /**
     * Get help text for this intent.
     * 
     * @return help text describing what this intent does
     */
    String getHelpText();
}
