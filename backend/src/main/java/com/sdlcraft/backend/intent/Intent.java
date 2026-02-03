package com.sdlcraft.backend.intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Intent represents a high-level developer goal.
 * 
 * An intent encapsulates what the developer wants to accomplish (e.g., "analyze security",
 * "improve performance"). Each intent has a name, description, parameters, examples, and
 * a default risk level.
 * 
 * Built-in intents: status, analyze, improve, test, debug, prepare, release
 */
public class Intent {
    
    private String name;
    private String description;
    private List<String> requiredParameters;
    private List<String> optionalParameters;
    private List<String> examples;
    private String defaultRiskLevel;
    private List<String> validTargets;
    
    public Intent() {
        this.requiredParameters = new ArrayList<>();
        this.optionalParameters = new ArrayList<>();
        this.examples = new ArrayList<>();
        this.validTargets = new ArrayList<>();
    }
    
    public Intent(String name, String description) {
        this.name = name;
        this.description = description;
        this.requiredParameters = new ArrayList<>();
        this.optionalParameters = new ArrayList<>();
        this.examples = new ArrayList<>();
        this.validTargets = new ArrayList<>();
        this.defaultRiskLevel = "LOW";
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getRequiredParameters() {
        return requiredParameters;
    }
    
    public void setRequiredParameters(List<String> requiredParameters) {
        this.requiredParameters = requiredParameters;
    }
    
    public void addRequiredParameter(String parameter) {
        this.requiredParameters.add(parameter);
    }
    
    public List<String> getOptionalParameters() {
        return optionalParameters;
    }
    
    public void setOptionalParameters(List<String> optionalParameters) {
        this.optionalParameters = optionalParameters;
    }
    
    public void addOptionalParameter(String parameter) {
        this.optionalParameters.add(parameter);
    }
    
    public List<String> getExamples() {
        return examples;
    }
    
    public void setExamples(List<String> examples) {
        this.examples = examples;
    }
    
    public void addExample(String example) {
        this.examples.add(example);
    }
    
    public String getDefaultRiskLevel() {
        return defaultRiskLevel;
    }
    
    public void setDefaultRiskLevel(String defaultRiskLevel) {
        this.defaultRiskLevel = defaultRiskLevel;
    }
    
    public List<String> getValidTargets() {
        return validTargets;
    }
    
    public void setValidTargets(List<String> validTargets) {
        this.validTargets = validTargets;
    }
    
    public void addValidTarget(String target) {
        this.validTargets.add(target);
    }
    
    public boolean isValidTarget(String target) {
        // If no valid targets specified, all targets are valid
        if (validTargets.isEmpty()) {
            return true;
        }
        return validTargets.contains(target);
    }
    
    @Override
    public String toString() {
        return "Intent{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", requiredParameters=" + requiredParameters +
                ", optionalParameters=" + optionalParameters +
                ", examples=" + examples +
                ", defaultRiskLevel='" + defaultRiskLevel + '\'' +
                ", validTargets=" + validTargets +
                '}';
    }
}
