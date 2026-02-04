package com.sdlcraft.backend.integration;

public record IntegrationHealth(
    String integrationId,
    boolean healthy,
    String message,
    long latencyMs
) {
    public static IntegrationHealth healthy(String id, long latencyMs) {
        return new IntegrationHealth(id, true, "Connected", latencyMs);
    }
    
    public static IntegrationHealth unhealthy(String id, String reason) {
        return new IntegrationHealth(id, false, reason, -1);
    }
}

