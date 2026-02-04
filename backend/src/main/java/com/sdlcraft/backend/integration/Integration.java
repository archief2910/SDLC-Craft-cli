package com.sdlcraft.backend.integration;

/**
 * Base interface for all external service integrations.
 * Each integration follows the agentic pattern: can be invoked by agents
 * to perform actions on external systems.
 */
public interface Integration {
    
    /**
     * Unique identifier for this integration.
     */
    String getId();
    
    /**
     * Human-readable name.
     */
    String getName();
    
    /**
     * Check if this integration is configured and ready.
     */
    boolean isConfigured();
    
    /**
     * Test connection to the external service.
     */
    IntegrationHealth healthCheck();
    
    /**
     * Get available actions this integration supports.
     */
    String[] getSupportedActions();
}

