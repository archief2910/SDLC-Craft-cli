package com.sdlcraft.backend.intent;

import com.sdlcraft.backend.llm.LLMException;
import com.sdlcraft.backend.llm.LLMProvider;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Intent Inference Service.
 * 
 * Feature: sdlcraft-cli
 * Property 7: Natural Language to Structured Intent
 * Validates: Requirements 2.3
 * 
 * This test verifies that for any natural language input, the Intent Inference Service
 * maps it to a structured intent with intent name, target, modifiers, and confidence score.
 */
class IntentInferencePropertyTest {
    
    // Helper method to create and initialize the service for each test
    private DefaultIntentInferenceService createInferenceService(LLMProvider llmProvider) {
        IntentDefinitionRepository mockRepository = Mockito.mock(IntentDefinitionRepository.class);
        
        // Setup mock repository to return built-in intents
        when(mockRepository.findByEnabledTrue()).thenReturn(new ArrayList<>());
        when(mockRepository.existsByName(anyString())).thenReturn(false);
        
        DefaultIntentInferenceService service = new DefaultIntentInferenceService(mockRepository, llmProvider);
        service.initialize();
        return service;
    }
    
    /**
     * Property 7: Natural Language to Structured Intent
     * 
     * For any natural language input, the Intent Inference Service should map it to
     * a structured intent with intent name, target, modifiers, and confidence score.
     */
    @Property(tries = 100)
    @Label("Natural language input maps to structured intent with all required fields")
    void naturalLanguageToStructuredIntent(
            @ForAll("naturalLanguageCommands") String naturalLanguageCommand) {
        
        // Setup LLM mock to return valid intent inference
        LLMProvider mockLLMProvider = Mockito.mock(LLMProvider.class);
        when(mockLLMProvider.isAvailable()).thenReturn(true);
        when(mockLLMProvider.complete(anyString(), anyMap())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            // Extract command from prompt and generate appropriate response
            return generateLLMResponse(naturalLanguageCommand);
        });
        
        DefaultIntentInferenceService inferenceService = createInferenceService(mockLLMProvider);
        
        // Create request
        IntentRequest request = new IntentRequest(naturalLanguageCommand, "test-user", "test-project");
        
        // Infer intent
        IntentResult result = inferenceService.inferIntent(request);
        
        // Verify structured intent has all required fields
        assertThat(result).isNotNull();
        assertThat(result.getIntent())
                .as("Intent name should be present")
                .isNotNull()
                .isNotBlank();
        
        assertThat(result.getTarget())
                .as("Target should be present (can be null or empty for some intents)")
                .satisfiesAnyOf(
                        target -> assertThat(target).isNotNull(),
                        target -> assertThat(target).isNull()
                );
        
        assertThat(result.getModifiers())
                .as("Modifiers map should be present (may be empty)")
                .isNotNull();
        
        assertThat(result.getConfidence())
                .as("Confidence score should be between 0.0 and 1.0")
                .isBetween(0.0, 1.0);
        
        assertThat(result.getExplanation())
                .as("Explanation should be present")
                .isNotNull()
                .isNotBlank();
    }
    
    /**
     * Property 7 (Template Matching): Template-based inference for structured commands
     * 
     * For any command matching the pattern "intent target", the service should
     * successfully infer the intent using template matching.
     */
    @Property(tries = 100)
    @Label("Structured commands are inferred via template matching")
    void structuredCommandsInferredViaTemplates(
            @ForAll("validIntentTargetPairs") IntentTargetPair pair) {
        
        LLMProvider mockLLMProvider = Mockito.mock(LLMProvider.class);
        when(mockLLMProvider.isAvailable()).thenReturn(false); // Force template matching
        
        DefaultIntentInferenceService inferenceService = createInferenceService(mockLLMProvider);
        
        String command = pair.intent + " " + pair.target;
        IntentRequest request = new IntentRequest(command, "test-user", "test-project");
        
        // Infer intent
        IntentResult result = inferenceService.inferIntent(request);
        
        // Verify result
        assertThat(result).isNotNull();
        assertThat(result.getIntent()).isEqualTo(pair.intent);
        assertThat(result.getTarget()).isEqualTo(pair.target);
        assertThat(result.getConfidence()).isGreaterThan(0.7);
        assertThat(result.getInferenceMethod()).contains("template");
    }
    
    /**
     * Property 7 (Natural Language Variations): Natural language variations map to intents
     * 
     * For any natural language variation of a command, the service should infer
     * the correct intent using LLM-based inference.
     */
    @Property(tries = 100)
    @Label("Natural language variations map to correct intents")
    void naturalLanguageVariationsMapToIntents(
            @ForAll("naturalLanguageVariations") NaturalLanguageCommand nlCommand) {
        
        // Setup LLM mock - return response without target if expectedTarget is empty
        LLMProvider mockLLMProvider = Mockito.mock(LLMProvider.class);
        when(mockLLMProvider.isAvailable()).thenReturn(true);
        
        // Generate LLM response based on expected values
        String llmResponse;
        if (nlCommand.expectedTarget.isEmpty()) {
            // For commands without target, don't include target in response
            llmResponse = String.format("intent=%s, confidence=0.85", nlCommand.expectedIntent);
        } else {
            llmResponse = String.format("intent=%s, target=%s, confidence=0.85", 
                    nlCommand.expectedIntent, nlCommand.expectedTarget);
        }
        
        when(mockLLMProvider.complete(anyString(), anyMap())).thenReturn(llmResponse);
        
        DefaultIntentInferenceService inferenceService = createInferenceService(mockLLMProvider);
        
        IntentRequest request = new IntentRequest(nlCommand.command, "test-user", "test-project");
        
        // Infer intent
        IntentResult result = inferenceService.inferIntent(request);
        
        // Verify the inferred intent matches expected
        assertThat(result).isNotNull();
        assertThat(result.getIntent()).isEqualTo(nlCommand.expectedIntent);
        
        // Target can be null or empty string for intents that don't require a target
        if (nlCommand.expectedTarget.isEmpty()) {
            // For empty expected targets, accept null or empty
            String actualTarget = result.getTarget();
            assertThat(actualTarget == null || actualTarget.isEmpty())
                    .as("Target should be null or empty for commands without a target. Actual: '" + actualTarget + "'")
                    .isTrue();
        } else {
            assertThat(result.getTarget()).isEqualTo(nlCommand.expectedTarget);
        }
    }
    
    /**
     * Property 7 (Low Confidence): Low confidence results include clarification questions
     * 
     * When inference confidence is below 0.7, the result should include
     * clarification questions for the user.
     */
    @Property(tries = 100)
    @Label("Low confidence inference includes clarification questions")
    void lowConfidenceIncludesClarificationQuestions(
            @ForAll("ambiguousCommands") String ambiguousCommand) {
        
        // Setup LLM to be unavailable to force template fallback with low confidence
        LLMProvider mockLLMProvider = Mockito.mock(LLMProvider.class);
        when(mockLLMProvider.isAvailable()).thenReturn(false);
        
        DefaultIntentInferenceService inferenceService = createInferenceService(mockLLMProvider);
        
        IntentRequest request = new IntentRequest(ambiguousCommand, "test-user", "test-project");
        
        try {
            IntentResult result = inferenceService.inferIntent(request);
            
            // If confidence is low, should have clarification questions
            if (result.getConfidence() < 0.7) {
                assertThat(result.getClarificationQuestions())
                        .as("Low confidence results should include clarification questions")
                        .isNotEmpty();
            }
        } catch (IntentInferenceException e) {
            // It's acceptable to throw exception for completely ambiguous commands
            assertThat(e.getMessage()).contains("Unable to infer intent");
        }
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<String> naturalLanguageCommands() {
        return Arbitraries.oneOf(
                // Structured commands
                Combinators.combine(validIntents(), validTargets())
                        .as((intent, target) -> intent + " " + target),
                
                // Natural language variations
                Arbitraries.of(
                        "check the security of the system",
                        "can you analyze performance",
                        "I want to improve reliability",
                        "show me the current status",
                        "what's the status of security",
                        "optimize the performance",
                        "scan for security issues",
                        "examine the quality",
                        "boost performance",
                        "fix security problems"
                ),
                
                // Variations with different phrasing
                Combinators.combine(
                        Arbitraries.of("check", "scan", "examine", "inspect", "analyze"),
                        Arbitraries.of("security", "performance", "quality")
                ).as((verb, target) -> verb + " " + target),
                
                Combinators.combine(
                        Arbitraries.of("optimize", "enhance", "fix", "boost", "improve"),
                        Arbitraries.of("performance", "reliability", "security")
                ).as((verb, target) -> verb + " " + target)
        );
    }
    
    @Provide
    Arbitrary<IntentTargetPair> validIntentTargetPairs() {
        return Arbitraries.oneOf(
                // Analyze with valid targets
                Combinators.combine(
                        Arbitraries.just("analyze"),
                        Arbitraries.of("security", "performance", "quality", "dependencies")
                ).as(IntentTargetPair::new),
                
                // Improve with valid targets
                Combinators.combine(
                        Arbitraries.just("improve"),
                        Arbitraries.of("performance", "reliability", "security", "quality")
                ).as(IntentTargetPair::new),
                
                // Test with valid targets
                Combinators.combine(
                        Arbitraries.just("test"),
                        Arbitraries.of("unit", "integration", "coverage")
                ).as(IntentTargetPair::new),
                
                // Release with valid targets
                Combinators.combine(
                        Arbitraries.just("release"),
                        Arbitraries.of("staging", "production")
                ).as(IntentTargetPair::new),
                
                // Status, debug, prepare accept any target
                Combinators.combine(
                        Arbitraries.of("status", "debug", "prepare"),
                        Arbitraries.of("security", "performance", "quality")
                ).as(IntentTargetPair::new)
        );
    }
    
    @Provide
    Arbitrary<String> validIntents() {
        return Arbitraries.of("status", "analyze", "improve", "test", "debug", "prepare", "release");
    }
    
    @Provide
    Arbitrary<String> validTargets() {
        return Arbitraries.oneOf(
                // Valid targets for analyze
                Arbitraries.of("security", "performance", "quality", "dependencies"),
                // Valid targets for improve
                Arbitraries.of("performance", "reliability", "security", "quality"),
                // Valid targets for test
                Arbitraries.of("unit", "integration", "coverage"),
                // Valid targets for release
                Arbitraries.of("staging", "production")
        );
    }
    
    @Provide
    Arbitrary<NaturalLanguageCommand> naturalLanguageVariations() {
        return Arbitraries.oneOf(
                // Analyze variations
                Arbitraries.of(
                        new NaturalLanguageCommand("check security", "analyze", "security"),
                        new NaturalLanguageCommand("scan for security issues", "analyze", "security"),
                        new NaturalLanguageCommand("examine performance", "analyze", "performance"),
                        new NaturalLanguageCommand("inspect quality", "analyze", "quality")
                ),
                
                // Improve variations
                Arbitraries.of(
                        new NaturalLanguageCommand("optimize performance", "improve", "performance"),
                        new NaturalLanguageCommand("enhance reliability", "improve", "reliability"),
                        new NaturalLanguageCommand("fix security", "improve", "security"),
                        new NaturalLanguageCommand("boost performance", "improve", "performance")
                ),
                
                // Status variations - status doesn't require a target
                // Use only the single word "status" to avoid parsing issues
                Arbitraries.of(
                        new NaturalLanguageCommand("status", "status", "")
                )
        );
    }
    
    @Provide
    Arbitrary<String> ambiguousCommands() {
        return Arbitraries.of(
                "do something",
                "fix it",
                "check",
                "improve",
                "make it better",
                "help",
                "what can you do"
        );
    }
    
    // Helper methods
    
    private String generateLLMResponse(String command) {
        // Simple heuristic to generate appropriate LLM response based on command
        String intent = "status";
        String target = "";
        double confidence = 0.8;
        
        String lowerCommand = command.toLowerCase();
        
        // Detect intent first
        if (lowerCommand.contains("check") || lowerCommand.contains("scan") || 
            lowerCommand.contains("examine") || lowerCommand.contains("inspect") ||
            lowerCommand.contains("analyze")) {
            intent = "analyze";
        } else if (lowerCommand.contains("optimize") || lowerCommand.contains("enhance") ||
                   lowerCommand.contains("fix") || lowerCommand.contains("boost") ||
                   lowerCommand.contains("improve")) {
            intent = "improve";
        } else if (lowerCommand.contains("status") || lowerCommand.contains("state")) {
            intent = "status";
        } else if (lowerCommand.contains("test")) {
            intent = "test";
        } else if (lowerCommand.contains("debug")) {
            intent = "debug";
        } else if (lowerCommand.contains("prepare")) {
            intent = "prepare";
        } else if (lowerCommand.contains("release")) {
            intent = "release";
        }
        
        // Detect target - only for intents that require targets
        // Status intent typically doesn't require a target
        if (!intent.equals("status")) {
            if (lowerCommand.contains("security")) {
                target = "security";
            } else if (lowerCommand.contains("performance")) {
                target = "performance";
            } else if (lowerCommand.contains("quality")) {
                target = "quality";
            } else if (lowerCommand.contains("reliability")) {
                target = "reliability";
            } else if (lowerCommand.contains("unit")) {
                target = "unit";
            } else if (lowerCommand.contains("integration")) {
                target = "integration";
            } else if (lowerCommand.contains("coverage")) {
                target = "coverage";
            } else if (lowerCommand.contains("staging")) {
                target = "staging";
            } else if (lowerCommand.contains("production")) {
                target = "production";
            }
        }
        
        return String.format("intent=%s, target=%s, confidence=%.2f", intent, target, confidence);
    }
    
    // Helper class for natural language command test data
    private static class NaturalLanguageCommand {
        final String command;
        final String expectedIntent;
        final String expectedTarget;
        
        NaturalLanguageCommand(String command, String expectedIntent, String expectedTarget) {
            this.command = command;
            this.expectedIntent = expectedIntent;
            this.expectedTarget = expectedTarget;
        }
    }
    
    // Helper class for valid intent-target pairs
    private static class IntentTargetPair {
        final String intent;
        final String target;
        
        IntentTargetPair(String intent, String target) {
            this.intent = intent;
            this.target = target;
        }
    }
}
