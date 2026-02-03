package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Controller for intelligent command suggestions.
 * 
 * Provides AI-powered command correction and suggestions
 * based on user input.
 */
@RestController
@RequestMapping("/api/intent")
public class SuggestController {
    
    private static final Logger logger = LoggerFactory.getLogger(SuggestController.class);
    
    private final LLMProvider llmProvider;
    
    // Valid commands
    private static final List<String> VALID_COMMANDS = Arrays.asList(
            "status", "analyze", "improve", "test", "debug", "prepare", "release", "refactor"
    );
    
    // Valid targets for each command
    private static final Map<String, List<String>> VALID_TARGETS = Map.of(
            "analyze", Arrays.asList("security", "performance", "quality", "dependencies"),
            "improve", Arrays.asList("performance", "reliability", "security", "quality"),
            "test", Arrays.asList("unit", "integration", "e2e", "coverage"),
            "release", Arrays.asList("staging", "production"),
            "refactor", Arrays.asList("code", "architecture", "design", "structure")
    );
    
    // Synonyms mapping
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("check", "status"),
            Map.entry("show", "status"),
            Map.entry("display", "status"),
            Map.entry("scan", "analyze"),
            Map.entry("inspect", "analyze"),
            Map.entry("audit", "analyze"),
            Map.entry("optimize", "improve"),
            Map.entry("enhance", "improve"),
            Map.entry("fix", "improve"),
            Map.entry("run", "test"),
            Map.entry("execute", "test"),
            Map.entry("verify", "test"),
            Map.entry("troubleshoot", "debug"),
            Map.entry("diagnose", "debug"),
            Map.entry("deploy", "release"),
            Map.entry("publish", "release"),
            Map.entry("setup", "prepare"),
            Map.entry("configure", "prepare"),
            Map.entry("cleanup", "refactor")
    );
    
    public SuggestController(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }
    
    /**
     * Get command suggestions based on input.
     */
    @PostMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggest(@RequestBody SuggestRequest request) {
        logger.info("Getting suggestions for: {}", request.getInput());
        
        try {
            String input = request.getInput().toLowerCase().trim();
            List<String> suggestions = new ArrayList<>();
            String explanation = "";
            
            // Step 1: Check for synonyms
            for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
                if (input.contains(entry.getKey())) {
                    String command = entry.getValue();
                    List<String> targets = VALID_TARGETS.getOrDefault(command, Collections.emptyList());
                    if (!targets.isEmpty()) {
                        for (String target : targets) {
                            suggestions.add(command + " " + target);
                        }
                    } else {
                        suggestions.add(command);
                    }
                    explanation = "'" + entry.getKey() + "' maps to '" + command + "'";
                    break;
                }
            }
            
            // Step 2: Check for typos using Levenshtein distance
            if (suggestions.isEmpty()) {
                String[] words = input.split("\\s+");
                for (String word : words) {
                    for (String command : VALID_COMMANDS) {
                        int distance = levenshteinDistance(word, command);
                        if (distance <= 2 && distance > 0) {
                            List<String> targets = VALID_TARGETS.getOrDefault(command, Collections.emptyList());
                            if (!targets.isEmpty()) {
                                for (String target : targets) {
                                    suggestions.add(command + " " + target);
                                }
                            } else {
                                suggestions.add(command);
                            }
                            explanation = "Did you mean '" + command + "' instead of '" + word + "'?";
                            break;
                        }
                    }
                    if (!suggestions.isEmpty()) break;
                }
            }
            
            // Step 3: Use LLM for complex queries
            if (suggestions.isEmpty() && llmProvider.isAvailable()) {
                suggestions = getLLMSuggestions(input);
                explanation = "Based on your description, here are relevant commands:";
            }
            
            // Step 4: Default suggestions
            if (suggestions.isEmpty()) {
                suggestions = getDefaultSuggestions(input);
                explanation = "Here are some commands that might help:";
            }
            
            // Remove duplicates and limit
            Set<String> uniqueSuggestions = new LinkedHashSet<>(suggestions);
            suggestions = new ArrayList<>(uniqueSuggestions);
            if (suggestions.size() > 5) {
                suggestions = suggestions.subList(0, 5);
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "suggestions", suggestions,
                    "explanation", explanation
            ));
            
        } catch (Exception e) {
            logger.error("Failed to get suggestions", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "suggestions", Collections.emptyList(),
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get suggestions from LLM.
     */
    private List<String> getLLMSuggestions(String input) {
        try {
            String prompt = "You are a CLI command assistant. The user wants to perform an SDLC task.\n\n" +
                    "Available commands:\n" +
                    "- status: Show project status\n" +
                    "- analyze security|performance|quality: Analyze codebase\n" +
                    "- improve performance|reliability|security: Improve code\n" +
                    "- test unit|integration|coverage: Run tests\n" +
                    "- debug: Debug issues\n" +
                    "- prepare release: Prepare for release\n" +
                    "- release staging|production: Deploy\n" +
                    "- refactor code|architecture: Refactor code\n\n" +
                    "User input: " + input + "\n\n" +
                    "Suggest 1-3 most relevant commands. Reply with just the commands, one per line.";
            
            String response = llmProvider.complete(prompt, Map.of(
                    "temperature", 0.3,
                    "max_tokens", 100
            ));
            
            List<String> suggestions = new ArrayList<>();
            for (String line : response.split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("-") && !line.startsWith("*")) {
                    // Remove common prefixes
                    line = line.replaceAll("^\\d+\\.\\s*", "");
                    line = line.replaceAll("^sdlc\\s+", "");
                    suggestions.add(line);
                }
            }
            
            return suggestions;
            
        } catch (Exception e) {
            logger.warn("LLM suggestion failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get default suggestions based on keywords.
     */
    private List<String> getDefaultSuggestions(String input) {
        List<String> suggestions = new ArrayList<>();
        
        if (input.contains("test") || input.contains("run")) {
            suggestions.add("test unit");
            suggestions.add("test coverage");
        }
        if (input.contains("secur") || input.contains("vuln")) {
            suggestions.add("analyze security");
        }
        if (input.contains("perf") || input.contains("fast") || input.contains("slow")) {
            suggestions.add("analyze performance");
            suggestions.add("improve performance");
        }
        if (input.contains("deploy") || input.contains("release") || input.contains("prod")) {
            suggestions.add("release staging");
            suggestions.add("release production");
        }
        if (input.contains("refactor") || input.contains("clean")) {
            suggestions.add("refactor code");
        }
        
        // Add general suggestions if nothing specific
        if (suggestions.isEmpty()) {
            suggestions.add("status");
            suggestions.add("analyze security");
            suggestions.add("test unit");
        }
        
        return suggestions;
    }
    
    /**
     * Calculate Levenshtein distance.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    // Request DTO
    public static class SuggestRequest {
        private String input;
        private String projectPath;
        
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        
        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    }
}




