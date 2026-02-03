package com.sdlcraft.backend.intent;

import java.util.List;

/**
 * IntentInferenceService converts natural language and ambiguous commands into structured intents.
 * 
 * This service acts as the bridge between user input and executable commands. It uses multiple
 * strategies to infer intent:
 * 1. Template matching for common patterns
 * 2. Historical context from long-term memory
 * 3. LLM-based inference for complex natural language
 * 
 * The service is designed to be extensible, allowing new intents to be registered dynamically
 * without code changes.
 * 
 * Requirements: 2.3, 9.5
 */
public interface IntentInferenceService {
    
    /**
     * Infers the structured intent from a natural language or ambiguous command.
     * 
     * This method attempts to understand what the user wants to accomplish and converts
     * it into a structured intent that can be executed by the agent orchestrator.
     * 
     * The inference process:
     * 1. Query long-term memory for similar past commands
     * 2. Apply intent templates for common patterns
     * 3. Use LLM for complex natural language (if confidence < 0.7 from templates)
     * 4. Return clarification questions if intent is still ambiguous
     * 
     * @param request the intent inference request containing command and context
     * @return the inferred intent with confidence score and explanation
     * @throws IntentInferenceException if inference fails completely
     */
    IntentResult inferIntent(IntentRequest request);
    
    /**
     * Returns all currently supported intents.
     * 
     * This includes both built-in intents (status, analyze, improve, test, debug, prepare, release)
     * and any dynamically registered custom intents.
     * 
     * @return list of all supported intents
     */
    List<Intent> getSupportedIntents();
    
    /**
     * Registers a new intent definition dynamically.
     * 
     * This allows the system to be extended with new intents without code changes.
     * The intent definition includes the intent name, description, required/optional
     * parameters, examples, and default risk level.
     * 
     * @param definition the intent definition to register
     * @throws IllegalArgumentException if the intent definition is invalid
     */
    void registerIntent(IntentDefinition definition);
    
    /**
     * Validates if an intent-target combination is valid.
     * 
     * Some intents only work with specific targets. For example, "analyze security"
     * is valid, but "analyze xyz" might not be. This method checks if the combination
     * makes sense based on registered intent definitions.
     * 
     * @param intent the intent name
     * @param target the target name
     * @return true if the combination is valid, false otherwise
     */
    boolean isValidIntentTargetCombination(String intent, String target);
}
