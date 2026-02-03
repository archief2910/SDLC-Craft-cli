package com.sdlcraft.backend.intent;

/**
 * Exception thrown when intent inference fails.
 * 
 * This exception is thrown when the system cannot infer the user's intent
 * even after trying all available strategies (templates, memory, LLM).
 */
public class IntentInferenceException extends RuntimeException {
    
    private final String rawCommand;
    private final String reason;
    
    public IntentInferenceException(String message) {
        super(message);
        this.rawCommand = null;
        this.reason = message;
    }
    
    public IntentInferenceException(String message, String rawCommand) {
        super(message);
        this.rawCommand = rawCommand;
        this.reason = message;
    }
    
    public IntentInferenceException(String message, Throwable cause) {
        super(message, cause);
        this.rawCommand = null;
        this.reason = message;
    }
    
    public IntentInferenceException(String message, String rawCommand, Throwable cause) {
        super(message, cause);
        this.rawCommand = rawCommand;
        this.reason = message;
    }
    
    public String getRawCommand() {
        return rawCommand;
    }
    
    public String getReason() {
        return reason;
    }
}
