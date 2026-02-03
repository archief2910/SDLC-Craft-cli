package com.sdlcraft.backend.memory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Context entry for semantic storage and retrieval.
 * 
 * Represents a piece of project context that can be retrieved
 * using semantic similarity search.
 */
public class ContextEntry {
    
    private String id;
    private String projectId;
    private String key;
    private String content;
    private Map<String, Object> metadata;
    private double similarityScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public ContextEntry() {
        this.metadata = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public ContextEntry(String id, String projectId, String key, String content) {
        this();
        this.id = id;
        this.projectId = projectId;
        this.key = key;
        this.content = content;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public double getSimilarityScore() {
        return similarityScore;
    }
    
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
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
