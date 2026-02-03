package com.sdlcraft.backend.intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IntentResult represents the outcome of intent inference.
 * 
 * This class contains the structured intent that was inferred from the user's input,
 * along with confidence score, explanation, and any clarification questions if the
 * intent is ambiguous.
 * 
 * Confidence levels:
 * - > 0.9: High confidence, proceed with execution
 * - 0.7-0.9: Medium confidence, confirm with user
 * - < 0.7: Low confidence, ask clarification questions
 */
public class IntentResult {
    
    private String intent;
    private String target;
    private Map<String, String> modifiers;
    private double confidence;
    private String explanation;
    private List<String> clarificationQuestions;
    private String inferenceMethod;
    
    public IntentResult() {
        this.modifiers = new HashMap<>();
        this.clarificationQuestions = new ArrayList<>();
    }
    
    public IntentResult(String intent, String target, double confidence, String explanation) {
        this.intent = intent;
        this.target = target;
        this.confidence = confidence;
        this.explanation = explanation;
        this.modifiers = new HashMap<>();
        this.clarificationQuestions = new ArrayList<>();
    }
    
    // Getters and setters
    
    public String getIntent() {
        return intent;
    }
    
    public void setIntent(String intent) {
        this.intent = intent;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public Map<String, String> getModifiers() {
        return modifiers;
    }
    
    public void setModifiers(Map<String, String> modifiers) {
        this.modifiers = modifiers;
    }
    
    public void addModifier(String key, String value) {
        this.modifiers.put(key, value);
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public List<String> getClarificationQuestions() {
        return clarificationQuestions;
    }
    
    public void setClarificationQuestions(List<String> clarificationQuestions) {
        this.clarificationQuestions = clarificationQuestions;
    }
    
    public void addClarificationQuestion(String question) {
        this.clarificationQuestions.add(question);
    }
    
    public String getInferenceMethod() {
        return inferenceMethod;
    }
    
    public void setInferenceMethod(String inferenceMethod) {
        this.inferenceMethod = inferenceMethod;
    }
    
    public boolean needsClarification() {
        return !clarificationQuestions.isEmpty() || confidence < 0.7;
    }
    
    @Override
    public String toString() {
        return "IntentResult{" +
                "intent='" + intent + '\'' +
                ", target='" + target + '\'' +
                ", modifiers=" + modifiers +
                ", confidence=" + confidence +
                ", explanation='" + explanation + '\'' +
                ", clarificationQuestions=" + clarificationQuestions +
                ", inferenceMethod='" + inferenceMethod + '\'' +
                '}';
    }
}
