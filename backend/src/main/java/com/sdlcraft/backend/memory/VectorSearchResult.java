package com.sdlcraft.backend.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result from vector similarity search.
 * 
 * Contains the matched document, its similarity score, and metadata.
 */
public class VectorSearchResult {
    
    private final String id;
    private final String content;
    private final double similarityScore;
    private final List<Double> embedding;
    private final Map<String, Object> metadata;
    
    public VectorSearchResult(String id, String content, double similarityScore,
                            List<Double> embedding, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.similarityScore = similarityScore;
        this.embedding = embedding;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    public String getId() {
        return id;
    }
    
    public String getContent() {
        return content;
    }
    
    public double getSimilarityScore() {
        return similarityScore;
    }
    
    public List<Double> getEmbedding() {
        return embedding;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
}
