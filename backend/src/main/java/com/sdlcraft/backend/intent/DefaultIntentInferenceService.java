package com.sdlcraft.backend.intent;

import com.sdlcraft.backend.llm.LLMException;
import com.sdlcraft.backend.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DefaultIntentInferenceService is the main implementation of IntentInferenceService.
 * 
 * This service uses a multi-strategy approach to infer intent:
 * 1. Template matching for common patterns (fast, deterministic)
 * 2. Historical context from long-term memory (learns from past)
 * 3. LLM-based inference for complex natural language (powerful, flexible)
 * 
 * The service maintains a registry of supported intents and validates intent-target
 * combinations before returning results.
 * 
 * Requirements: 2.3, 5.3, 9.5
 */
@Service
public class DefaultIntentInferenceService implements IntentInferenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultIntentInferenceService.class);
    
    private final IntentDefinitionRepository intentRepository;
    private final LLMProvider llmProvider;
    
    // In-memory cache of intents for fast lookup
    private final Map<String, Intent> intentCache = new ConcurrentHashMap<>();
    
    // Intent templates for pattern matching
    private final List<IntentTemplate> templates = new ArrayList<>();
    
    @Autowired
    public DefaultIntentInferenceService(
            IntentDefinitionRepository intentRepository,
            LLMProvider llmProvider) {
        this.intentRepository = intentRepository;
        this.llmProvider = llmProvider;
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing Intent Inference Service");
        
        // Load intents from database
        loadIntentsFromDatabase();
        
        // Initialize built-in intents if not present
        initializeBuiltInIntents();
        
        // Build intent templates
        buildIntentTemplates();
        
        logger.info("Intent Inference Service initialized with {} intents", intentCache.size());
    }
    
    @Override
    public IntentResult inferIntent(IntentRequest request) {
        logger.debug("Inferring intent for command: {}", request.getRawCommand());
        
        if (request.getRawCommand() == null || request.getRawCommand().trim().isEmpty()) {
            throw new IntentInferenceException("Command cannot be empty");
        }
        
        String command = request.getRawCommand().trim();
        
        // Strategy 1: Try template matching (fast, deterministic)
        IntentResult templateResult = tryTemplateMatching(command);
        if (templateResult != null && templateResult.getConfidence() > 0.7) {
            templateResult.setInferenceMethod("template");
            logger.debug("Intent inferred via template: {} (confidence: {})", 
                    templateResult.getIntent(), templateResult.getConfidence());
            return templateResult;
        }
        
        // Strategy 2: Try LLM-based inference (powerful, flexible)
        if (llmProvider.isAvailable()) {
            try {
                IntentResult llmResult = tryLLMInference(command, request);
                if (llmResult != null) {
                    llmResult.setInferenceMethod("llm");
                    logger.debug("Intent inferred via LLM: {} (confidence: {})", 
                            llmResult.getIntent(), llmResult.getConfidence());
                    return llmResult;
                }
            } catch (LLMException e) {
                logger.warn("LLM inference failed, falling back to template result: {}", e.getMessage());
                // Fall back to template result if available
                if (templateResult != null) {
                    templateResult.setInferenceMethod("template-fallback");
                    return templateResult;
                }
            }
        }
        
        // Strategy 3: Return template result with clarification questions
        if (templateResult != null) {
            if (templateResult.getConfidence() < 0.7) {
                templateResult.addClarificationQuestion(
                        "Did you mean '" + templateResult.getIntent() + " " + templateResult.getTarget() + "'?");
                templateResult.addClarificationQuestion(
                        "Please provide more details about what you want to accomplish.");
            }
            templateResult.setInferenceMethod("template-with-clarification");
            return templateResult;
        }
        
        // All strategies failed
        throw new IntentInferenceException(
                "Unable to infer intent from command: " + command, command);
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return new ArrayList<>(intentCache.values());
    }
    
    @Override
    public void registerIntent(IntentDefinition definition) {
        logger.info("Registering intent: {}", definition.getName());
        
        // Validate definition
        if (definition.getName() == null || definition.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Intent name cannot be empty");
        }
        
        // Save to database
        intentRepository.save(definition);
        
        // Update cache
        intentCache.put(definition.getName(), definition.toIntent());
        
        // Rebuild templates
        buildIntentTemplates();
        
        logger.info("Intent registered successfully: {}", definition.getName());
    }
    
    @Override
    public boolean isValidIntentTargetCombination(String intent, String target) {
        Intent intentObj = intentCache.get(intent);
        if (intentObj == null) {
            return false;
        }
        return intentObj.isValidTarget(target);
    }
    
    // Private helper methods
    
    private void loadIntentsFromDatabase() {
        List<IntentDefinition> definitions = intentRepository.findByEnabledTrue();
        for (IntentDefinition def : definitions) {
            intentCache.put(def.getName(), def.toIntent());
        }
        logger.info("Loaded {} intents from database", definitions.size());
    }
    
    private void initializeBuiltInIntents() {
        // Define built-in intents if not already in database
        String[] builtInIntents = {"status", "analyze", "improve", "test", "debug", "prepare", "release", "refactor"};
        
        for (String intentName : builtInIntents) {
            if (!intentRepository.existsByName(intentName)) {
                IntentDefinition def = createBuiltInIntent(intentName);
                intentRepository.save(def);
                intentCache.put(intentName, def.toIntent());
                logger.info("Initialized built-in intent: {}", intentName);
            }
        }
    }
    
    private IntentDefinition createBuiltInIntent(String name) {
        IntentDefinition def = new IntentDefinition(name, getBuiltInDescription(name));
        def.setDefaultRiskLevel(getBuiltInRiskLevel(name));
        def.setValidTargets(getBuiltInValidTargets(name));
        def.setExamples(getBuiltInExamples(name));
        return def;
    }
    
    private String getBuiltInDescription(String name) {
        return switch (name) {
            case "status" -> "Display current SDLC state and metrics";
            case "analyze" -> "Perform analysis on specified target";
            case "improve" -> "Suggest and apply improvements to specified target";
            case "refactor" -> "Refactor code to improve quality and maintainability";
            case "test" -> "Run tests on specified target";
            case "debug" -> "Debug issues in specified target";
            case "prepare" -> "Prepare for specified action";
            case "release" -> "Release to specified environment";
            default -> "Custom intent: " + name;
        };
    }
    
    private String getBuiltInRiskLevel(String name) {
        return switch (name) {
            case "release" -> "HIGH";
            case "improve", "prepare", "refactor" -> "MEDIUM";
            default -> "LOW";
        };
    }
    
    private List<String> getBuiltInValidTargets(String name) {
        return switch (name) {
            case "analyze" -> Arrays.asList("security", "performance", "quality", "dependencies");
            case "improve" -> Arrays.asList("performance", "reliability", "security", "quality");
            case "refactor" -> Arrays.asList("code", "architecture", "design", "structure");
            case "test" -> Arrays.asList("unit", "integration", "e2e", "coverage");
            case "release" -> Arrays.asList("staging", "production");
            default -> new ArrayList<>(); // All targets valid
        };
    }
    
    private List<String> getBuiltInExamples(String name) {
        return switch (name) {
            case "status" -> Arrays.asList("sdlc status", "sdlc status security");
            case "analyze" -> Arrays.asList("sdlc analyze security", "sdlc analyze performance");
            case "improve" -> Arrays.asList("sdlc improve performance", "sdlc improve reliability");
            case "refactor" -> Arrays.asList("sdlc refactor code", "sdlc refactor architecture");
            case "test" -> Arrays.asList("sdlc test unit", "sdlc test coverage");
            case "debug" -> Arrays.asList("sdlc debug failure", "sdlc debug error");
            case "prepare" -> Arrays.asList("sdlc prepare release", "sdlc prepare deployment");
            case "release" -> Arrays.asList("sdlc release staging", "sdlc release production");
            default -> new ArrayList<>();
        };
    }
    
    private void buildIntentTemplates() {
        templates.clear();
        
        // Build templates from all intents
        for (Intent intent : intentCache.values()) {
            // Template: "intent target"
            templates.add(new IntentTemplate(
                    Pattern.compile("(?i)\\b" + intent.getName() + "\\s+(\\w+)\\b"),
                    intent.getName(),
                    1, // target group
                    0.9
            ));
            
            // Template: "target intent" (reversed)
            templates.add(new IntentTemplate(
                    Pattern.compile("(?i)\\b(\\w+)\\s+" + intent.getName() + "\\b"),
                    intent.getName(),
                    1, // target group
                    0.85
            ));
            
            // Template: natural language variations
            if (intent.getName().equals("analyze")) {
                templates.add(new IntentTemplate(
                        Pattern.compile("(?i)\\b(check|scan|examine|inspect)\\s+(\\w+)\\b"),
                        "analyze",
                        2, // target group
                        0.8
                ));
            }
            
            if (intent.getName().equals("improve")) {
                templates.add(new IntentTemplate(
                        Pattern.compile("(?i)\\b(optimize|enhance|fix|boost)\\s+(\\w+)\\b"),
                        "improve",
                        2, // target group
                        0.8
                ));
            }
        }
        
        logger.debug("Built {} intent templates", templates.size());
    }
    
    private IntentResult tryTemplateMatching(String command) {
        for (IntentTemplate template : templates) {
            Matcher matcher = template.pattern.matcher(command);
            if (matcher.find()) {
                String target = matcher.group(template.targetGroup);
                
                // Validate intent-target combination
                // If no valid targets defined, accept any target
                Intent intent = intentCache.get(template.intent);
                if (intent != null && (intent.getValidTargets().isEmpty() || isValidIntentTargetCombination(template.intent, target))) {
                    IntentResult result = new IntentResult(
                            template.intent,
                            target,
                            template.confidence,
                            "Matched template pattern for '" + template.intent + "'"
                    );
                    return result;
                }
            }
        }
        return null;
    }
    
    private IntentResult tryLLMInference(String command, IntentRequest request) {
        String prompt = buildLLMPrompt(command, request);
        
        try {
            String response = llmProvider.complete(prompt, Map.of(
                    "temperature", 0.3,
                    "max_tokens", 200
            ));
            
            return parseLLMResponse(response, command);
        } catch (LLMException e) {
            logger.error("LLM inference failed: {}", e.getMessage());
            throw e;
        }
    }
    
    private String buildLLMPrompt(String command, IntentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an intent inference system for an SDLC CLI tool.\n\n");
        prompt.append("Supported intents: ").append(String.join(", ", intentCache.keySet())).append("\n\n");
        prompt.append("User command: ").append(command).append("\n\n");
        prompt.append("Infer the intent and target from the command.\n");
        prompt.append("Respond in format: intent=<intent>, target=<target>, confidence=<0.0-1.0>\n");
        return prompt.toString();
    }
    
    private IntentResult parseLLMResponse(String response, String command) {
        // Parse LLM response (format: intent=X, target=Y, confidence=Z)
        String intent = extractValue(response, "intent");
        String target = extractValue(response, "target");
        double confidence = Double.parseDouble(extractValue(response, "confidence", "0.7"));
        
        if (intent == null || !intentCache.containsKey(intent)) {
            throw new IntentInferenceException("LLM returned invalid intent: " + intent);
        }
        
        return new IntentResult(
                intent,
                target,
                confidence,
                "Inferred via LLM from natural language"
        );
    }
    
    private String extractValue(String text, String key) {
        return extractValue(text, key, null);
    }
    
    private String extractValue(String text, String key, String defaultValue) {
        Pattern pattern = Pattern.compile(key + "\\s*=\\s*([^,\\n]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }
    
    // Inner class for intent templates
    private static class IntentTemplate {
        final Pattern pattern;
        final String intent;
        final int targetGroup;
        final double confidence;
        
        IntentTemplate(Pattern pattern, String intent, int targetGroup, double confidence) {
            this.pattern = pattern;
            this.intent = intent;
            this.targetGroup = targetGroup;
            this.confidence = confidence;
        }
    }
}
