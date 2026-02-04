package com.sdlcraft.backend.integration;

import java.util.Map;

/**
 * Result from an integration action execution.
 */
public record IntegrationResult(
    String integrationId,
    String action,
    boolean success,
    String message,
    Map<String, Object> data,
    long executionTimeMs
) {
    public static IntegrationResult success(String id, String action, String message, Map<String, Object> data, long timeMs) {
        return new IntegrationResult(id, action, true, message, data, timeMs);
    }
    
    public static IntegrationResult failure(String id, String action, String error) {
        return new IntegrationResult(id, action, false, error, Map.of(), -1);
    }
}

