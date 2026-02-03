package com.sdlcraft.backend.config;

import com.sdlcraft.backend.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for registering agents with the orchestrator at startup.
 */
@Configuration
public class AgentConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfiguration.class);
    
    @Bean
    public CommandLineRunner registerAgents(AgentOrchestrator orchestrator,
                                           PlannerAgent plannerAgent,
                                           ExecutorAgent executorAgent,
                                           ValidatorAgent validatorAgent,
                                           ReflectionAgent reflectionAgent) {
        return args -> {
            logger.info("Registering agents with orchestrator...");
            
            orchestrator.registerAgent(plannerAgent);
            orchestrator.registerAgent(executorAgent);
            orchestrator.registerAgent(validatorAgent);
            orchestrator.registerAgent(reflectionAgent);
            
            logger.info("Successfully registered {} agents", orchestrator.getRegisteredAgents().size());
        };
    }
}
