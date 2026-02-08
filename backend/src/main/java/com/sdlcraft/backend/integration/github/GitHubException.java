package com.sdlcraft.backend.integration.github;

/**
 * Exception thrown when GitHub operations fail.
 */
public class GitHubException extends RuntimeException {
    
    public GitHubException(String message) {
        super(message);
    }
    
    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}

