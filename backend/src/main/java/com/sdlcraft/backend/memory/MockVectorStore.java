package com.sdlcraft.backend.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-Memory vector store implementation for local development.
 * 
 * Uses in-memory storage and simple cosine similarity for search.
 * Works without external vector database (Pinecone, etc.).
 * 
 * Design rationale:
 * - Enables development without external vector database
 * - Provides reference implementation of VectorStore interface
 * - Uses cosine similarity for semantic search
 * - Thread-safe with ConcurrentHashMap
 */
@Component
@Primary
public class MockVectorStore implements VectorStore {
    
    private static final Logger logger = LoggerFactory.getLogger(MockVectorStore.class);
    
    private final Map<String, VectorDocument> documents = new ConcurrentHashMap<>();
    
    @Override
    public void store(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
        VectorDocument doc = new VectorDocument(id, content, embedding, metadata);
        documents.put(id, doc);
        logger.debug("Stored document: {}", id);
    }
    
    @Override
    public void storeWithAutoEmbedding(String id, String content, Map<String, Object> metadata) {
        // Generate simple embedding from content
        // In production, use actual embedding model (OpenAI, Sentence Transformers, etc.)
        List<Double> embedding = generateSimpleEmbedding(content);
        store(id, content, embedding, metadata);
    }
    
    @Override
    public List<VectorSearchResult> search(List<Double> queryEmbedding, int limit, Map<String, Object> filter) {
        List<VectorSearchResult> results = new ArrayList<>();
        
        for (VectorDocument doc : documents.values()) {
            // Apply filter if provided
            if (filter != null && !matchesFilter(doc, filter)) {
                continue;
            }
            
            // Calculate cosine similarity
            double similarity = cosineSimilarity(queryEmbedding, doc.getEmbedding());
            
            results.add(new VectorSearchResult(
                    doc.getId(),
                    doc.getContent(),
                    similarity,
                    doc.getEmbedding(),
                    doc.getMetadata()
            ));
        }
        
        // Sort by similarity (descending) and limit
        return results.stream()
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<VectorSearchResult> searchByText(String queryText, int limit, Map<String, Object> filter) {
        List<Double> queryEmbedding = generateSimpleEmbedding(queryText);
        return search(queryEmbedding, limit, filter);
    }
    
    @Override
    public boolean delete(String id) {
        VectorDocument removed = documents.remove(id);
        if (removed != null) {
            logger.debug("Deleted document: {}", id);
            return true;
        }
        return false;
    }
    
    @Override
    public int deleteByFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return 0;
        }
        
        List<String> toDelete = new ArrayList<>();
        for (VectorDocument doc : documents.values()) {
            if (matchesFilter(doc, filter)) {
                toDelete.add(doc.getId());
            }
        }
        
        for (String id : toDelete) {
            documents.remove(id);
        }
        
        logger.debug("Deleted {} documents by filter", toDelete.size());
        return toDelete.size();
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Mock store is always available
    }
    
    @Override
    public String getImplementationName() {
        return "mock-vector-store";
    }
    
    /**
     * Generate simple embedding from text.
     * 
     * This is a placeholder implementation. In production, use:
     * - OpenAI embeddings API
     * - Sentence Transformers
     * - Other embedding models
     */
    private List<Double> generateSimpleEmbedding(String text) {
        // Simple hash-based embedding (not semantically meaningful)
        // Just for demonstration purposes
        Random random = new Random(text.hashCode());
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 384; i++) { // 384-dimensional embedding
            embedding.add(random.nextDouble() * 2 - 1); // Range: [-1, 1]
        }
        return embedding;
    }
    
    /**
     * Calculate cosine similarity between two vectors.
     */
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Check if document matches filter criteria.
     */
    private boolean matchesFilter(VectorDocument doc, Map<String, Object> filter) {
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object docValue = doc.getMetadata().get(entry.getKey());
            if (!Objects.equals(docValue, entry.getValue())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Internal document representation.
     */
    private static class VectorDocument {
        private final String id;
        private final String content;
        private final List<Double> embedding;
        private final Map<String, Object> metadata;
        
        public VectorDocument(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
            this.id = id;
            this.content = content;
            this.embedding = new ArrayList<>(embedding);
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }
        
        public String getId() {
            return id;
        }
        
        public String getContent() {
            return content;
        }
        
        public List<Double> getEmbedding() {
            return embedding;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
