package com.sdlcraft.backend.config;

import com.sdlcraft.backend.memory.SimpleCodebaseIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for automatic codebase indexing on startup.
 * 
 * Indexes the SDLCraft project so RAG queries can find relevant code.
 * Uses simple file scanning (like micro-agent) - no vector DB needed!
 */
@Configuration
public class CodebaseIndexingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CodebaseIndexingConfig.class);
    
    @Bean
    public CommandLineRunner indexCodebaseOnStartup(SimpleCodebaseIndexer codebaseIndexer) {
        return args -> {
            try {
                // Find the project root (go up from backend directory)
                Path currentDir = Paths.get("").toAbsolutePath();
                Path projectRoot = currentDir;
                
                // If we're in the backend directory, go up one level
                if (currentDir.endsWith("backend")) {
                    projectRoot = currentDir.getParent();
                }
                
                logger.info("╔════════════════════════════════════════════════════════════╗");
                logger.info("║  🔍 Auto-indexing codebase for RAG...                       ║");
                logger.info("║  Project root: {}                                           ", projectRoot);
                logger.info("╚════════════════════════════════════════════════════════════╝");
                
                // Index the codebase
                codebaseIndexer.indexCodebase(projectRoot.toString());
                
                logger.info("✅ Codebase indexing complete! AI can now analyze your code.");
                
            } catch (Exception e) {
                logger.error("⚠️ Failed to auto-index codebase: {}", e.getMessage());
                logger.info("You can manually index using: POST /api/rag/index");
            }
        };
    }
}

