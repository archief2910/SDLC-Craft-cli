package com.sdlcraft.backend.integration.jira;

/**
 * Exception thrown when Jira operations fail.
 */
public class JiraException extends RuntimeException {
    
    public JiraException(String message) {
        super(message);
    }
    
    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }
}

