package com.sdlcraft.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for SDLCraft Backend.
 * 
 * This backend service handles:
 * - Intent inference from natural language and ambiguous commands
 * - Agent orchestration following PLAN → ACT → OBSERVE → REFLECT pattern
 * - SDLC state tracking and management
 * - Long-term memory and context storage
 * - Policy enforcement and safety checks
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
