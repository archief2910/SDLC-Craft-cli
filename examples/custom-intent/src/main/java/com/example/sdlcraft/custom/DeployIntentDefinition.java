package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentDefinition;
import com.sdlcraft.backend.sdlc.RiskLevel;
import java.util.List;

/**
 * Definition for the custom 'deploy' intent.
 * 
 * This intent allows users to deploy applications to different environments
 * with optional version specification and rollback capabilities.
 */
public class DeployIntentDefinition {
    
    public static IntentDefinition create() {
        IntentDefinition definition = new IntentDefinition();
        
        // Basic information
        definition.setName("deploy");
        definition.setDescription("Deploy application to specified environment");
        
        // Parameters
        definition.setRequiredParameters(List.of("environment"));
        definition.setOptionalParameters(List.of("version", "rollback", "dry-run"));
        
        // Example commands
        definition.setExamples(List.of(
            "sdlc deploy staging",
            "sdlc deploy production --version=1.2.3",
            "sdlc deploy staging --rollback=true",
            "deploy to production with version 2.0.0",
            "deploy my app to staging"
        ));
        
        // Risk classification
        // Production deployments are HIGH risk, others are MEDIUM
        definition.setDefaultRiskLevel(RiskLevel.HIGH);
        
        return definition;
    }
}
