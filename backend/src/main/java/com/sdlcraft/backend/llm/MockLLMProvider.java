package com.sdlcraft.backend.llm;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MockLLMProvider is a test implementation of LLMProvider.
 * 
 * This provider is used for development and testing when a real LLM is not available.
 * It provides simple pattern-based responses and random embeddings.
 * 
 * Activated with the "mock-llm" profile.
 */
@Component
@Profile({"mock-llm", "test"})
public class MockLLMProvider implements LLMProvider {
    
    private final Random random = new Random();
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        // Simple pattern matching for common prompts
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("infer intent") || lowerPrompt.contains("what does the user want")) {
            return extractMockIntent(prompt);
        }
        
        if (lowerPrompt.contains("clarification")) {
            return "What specific aspect would you like to focus on?";
        }
        
        // Default response
        return "Mock LLM response for: " + prompt.substring(0, Math.min(50, prompt.length()));
    }
    
    @Override
    public List<double[]> embed(List<String> texts) {
        // Generate random embeddings (384 dimensions, typical for sentence transformers)
        List<double[]> embeddings = new ArrayList<>();
        
        for (String text : texts) {
            double[] embedding = new double[384];
            // Use text hash as seed for reproducibility
            Random textRandom = new Random(text.hashCode());
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = textRandom.nextGaussian();
            }
            // Normalize
            double norm = 0;
            for (double v : embedding) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
            embeddings.add(embedding);
        }
        
        return embeddings;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public String getProviderName() {
        return "Mock LLM Provider";
    }
    
    private String extractMockIntent(String prompt) {
        // Extract command from prompt (simple heuristic)
        String[] lines = prompt.split("\n");
        for (String line : lines) {
            if (line.contains("command:") || line.contains("input:")) {
                String command = line.substring(line.indexOf(":") + 1).trim();
                return inferFromCommand(command);
            }
        }
        return "status";
    }
    
    private String inferFromCommand(String command) {
        String lower = command.toLowerCase();
        
        // Simple keyword matching
        if (lower.contains("security") || lower.contains("secure") || lower.contains("vulnerability")) {
            return "analyze security";
        }
        if (lower.contains("performance") || lower.contains("speed") || lower.contains("fast")) {
            return "improve performance";
        }
        if (lower.contains("test") || lower.contains("coverage")) {
            return "test coverage";
        }
        if (lower.contains("deploy") || lower.contains("release")) {
            return "release";
        }
        if (lower.contains("debug") || lower.contains("error") || lower.contains("bug")) {
            return "debug";
        }
        
        // Default
        return "status";
    }
}
