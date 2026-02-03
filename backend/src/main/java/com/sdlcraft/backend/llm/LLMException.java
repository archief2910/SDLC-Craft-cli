package com.sdlcraft.backend.llm;

/**
 * Exception thrown when LLM operations fail.
 * 
 * This exception wraps errors from LLM providers (API errors, timeouts, rate limits, etc.)
 * and provides a consistent error handling interface.
 */
public class LLMException extends RuntimeException {
    
    private final String providerName;
    private final String errorCode;
    
    public LLMException(String message) {
        super(message);
        this.providerName = null;
        this.errorCode = null;
    }
    
    public LLMException(String message, String providerName) {
        super(message);
        this.providerName = providerName;
        this.errorCode = null;
    }
    
    public LLMException(String message, String providerName, String errorCode) {
        super(message);
        this.providerName = providerName;
        this.errorCode = errorCode;
    }
    
    public LLMException(String message, Throwable cause) {
        super(message, cause);
        this.providerName = null;
        this.errorCode = null;
    }
    
    public LLMException(String message, String providerName, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorCode = null;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
