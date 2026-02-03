package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentInferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Registers custom intents with the system on startup.
 * 
 * This component runs after the Spring context is initialized and registers
 * all custom intent definitions with the IntentInferenceService.
 */
@Component
public class CustomIntentRegistrar implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomIntentRegistrar.class);
    
    private final IntentInferenceService intentService;
    
    public CustomIntentRegistrar(IntentInferenceService intentService) {
        this.intentService = intentService;
    }
    
    @Override
    public void run(String... args) {
        logger.info("Registering custom intents...");
        
        try {
            // Register the deploy intent
            intentService.registerIntent(DeployIntentDefinition.create());
            logger.info("✓ Registered custom intent: deploy");
            
            // Add more custom intents here as needed
            // intentService.registerIntent(AnotherIntentDefinition.create());
            
        } catch (Exception e) {
            logger.error("Failed to register custom intents", e);
            throw new RuntimeException("Custom intent registration failed", e);
        }
    }
}
