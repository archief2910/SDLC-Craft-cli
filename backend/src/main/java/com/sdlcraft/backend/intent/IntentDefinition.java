package com.sdlcraft.backend.intent;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IntentDefinition is a JPA entity that stores intent definitions in the database.
 * 
 * This allows intents to be registered dynamically without code changes. Intent definitions
 * are loaded from the database on startup and can be added/modified at runtime.
 * 
 * Requirements: 2.4, 11.1, 11.4
 */
@Entity
@Table(name = "intent_definitions")
public class IntentDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @ElementCollection
    @CollectionTable(name = "intent_required_parameters", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "parameter")
    private List<String> requiredParameters = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "intent_optional_parameters", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "parameter")
    private List<String> optionalParameters = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "intent_examples", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "example", length = 500)
    private List<String> examples = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "intent_valid_targets", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "target")
    private List<String> validTargets = new ArrayList<>();
    
    @Column(nullable = false)
    private String defaultRiskLevel = "LOW";
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    
    public IntentDefinition() {
    }
    
    public IntentDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    // Convert to Intent
    
    public Intent toIntent() {
        Intent intent = new Intent(name, description);
        intent.setRequiredParameters(new ArrayList<>(requiredParameters));
        intent.setOptionalParameters(new ArrayList<>(optionalParameters));
        intent.setExamples(new ArrayList<>(examples));
        intent.setDefaultRiskLevel(defaultRiskLevel);
        intent.setValidTargets(new ArrayList<>(validTargets));
        return intent;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
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
    
    public List<String> getOptionalParameters() {
        return optionalParameters;
    }
    
    public void setOptionalParameters(List<String> optionalParameters) {
        this.optionalParameters = optionalParameters;
    }
    
    public List<String> getExamples() {
        return examples;
    }
    
    public void setExamples(List<String> examples) {
        this.examples = examples;
    }
    
    public List<String> getValidTargets() {
        return validTargets;
    }
    
    public void setValidTargets(List<String> validTargets) {
        this.validTargets = validTargets;
    }
    
    public String getDefaultRiskLevel() {
        return defaultRiskLevel;
    }
    
    public void setDefaultRiskLevel(String defaultRiskLevel) {
        this.defaultRiskLevel = defaultRiskLevel;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
