package com.example.sdlcraft.custom;

import com.sdlcraft.backend.memory.VectorStore;
import com.sdlcraft.backend.memory.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pinecone Vector Store implementation.
 * 
 * Integrates with Pinecone for:
 * - Storing embeddings with metadata
 * - Semantic similarity search
 * - Vector deletion
 * 
 * Requires PINECONE_API_KEY, PINECONE_ENVIRONMENT, and PINECONE_INDEX
 * environment variables.
 * 
 * @author SDLCraft Team
 * @since 1.0.0
 */
@Component
public class PineconeVectorStore implements VectorStore {
    
    private static final Logger logger = LoggerFactory.getLogger(PineconeVectorStore.class);
    
    private final String apiKey;
    private final String environment;
    private final String indexName;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    
    public PineconeVectorStore(
            @Value("${sdlcraft.vector-store.pinecone.api-key}") String apiKey,
            @Value("${sdlcraft.vector-store.pinecone.environment}") String environment,
            @Value("${sdlcraft.vector-store.pinecone.index}") String indexName) {
        this.apiKey = apiKey;
        this.environment = environment;
        this.indexName = indexName;
        this.restTemplate = new RestTemplate();
        this.baseUrl = String.format(
            "https://%s-%s.svc.%s.pinecone.io",
            indexName, environment, environment
        );
        
        logger.info("Initialized Pinecone vector store: {}", indexName);
    }
    
    @Override
    public void store(String id, List<Double> embedding, Map<String, Object> metadata) {
        logger.debug("Storing vector in Pinecone: {}", id);
        
        try {
            String url = baseUrl + "/vectors/upsert";
            
            // Build vector object
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", id);
            vector.put("values", embedding);
            if (metadata != null && !metadata.isEmpty()) {
                vector.put("metadata", metadata);
            }
            
            // Build request
            Map<String, Object> request = Map.of(
                "vectors", List.of(vector)
            );
            
            // Call Pinecone API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully stored vector: {}", id);
            } else {
                logger.warn("Unexpected response status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to store vector in Pinecone: {}", id, e);
            throw new RuntimeException("Failed to store vector in Pinecone", e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(
            List<Double> queryEmbedding, 
            int limit, 
            Map<String, Object> filter) {
        logger.debug("Searching vectors in Pinecone (limit: {})", limit);
        
        try {
            String url = baseUrl + "/query";
            
            // Build request
            Map<String, Object> request = new HashMap<>();
            request.put("vector", queryEmbedding);
            request.put("topK", limit);
            request.put("includeMetadata", true);
            
            if (filter != null && !filter.isEmpty()) {
                request.put("filter", filter);
            }
            
            // Call Pinecone API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                logger.warn("Empty response from Pinecone");
                return List.of();
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = 
                (List<Map<String, Object>>) body.get("matches");
            
            if (matches == null || matches.isEmpty()) {
                logger.debug("No matches found in Pinecone");
                return List.of();
            }
            
            // Convert to VectorSearchResult
            List<VectorSearchResult> results = matches.stream()
                .map(match -> {
                    String id = (String) match.get("id");
                    double score = ((Number) match.get("score")).doubleValue();
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = 
                        (Map<String, Object>) match.get("metadata");
                    
                    return new VectorSearchResult(id, score, metadata);
                })
                .collect(Collectors.toList());
            
            logger.debug("Found {} matches in Pinecone", results.size());
            
            return results;
            
        } catch (Exception e) {
            logger.error("Failed to search vectors in Pinecone", e);
            throw new RuntimeException("Failed to search vectors in Pinecone", e);
        }
    }
    
    @Override
    public void delete(String id) {
        logger.debug("Deleting vector from Pinecone: {}", id);
        
        try {
            String url = baseUrl + "/vectors/delete";
            
            // Build request
            Map<String, Object> request = Map.of(
                "ids", List.of(id)
            );
            
            // Call Pinecone API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully deleted vector: {}", id);
            } else {
                logger.warn("Unexpected response status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to delete vector from Pinecone: {}", id, e);
            throw new RuntimeException("Failed to delete vector from Pinecone", e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "pinecone";
    }
    
    /**
     * Batch store multiple vectors for better performance.
     * 
     * @param vectors Map of vector IDs to embeddings
     * @param metadata Map of vector IDs to metadata
     */
    public void storeBatch(
            Map<String, List<Double>> vectors, 
            Map<String, Map<String, Object>> metadata) {
        logger.debug("Batch storing {} vectors in Pinecone", vectors.size());
        
        try {
            String url = baseUrl + "/vectors/upsert";
            
            // Build vector objects
            List<Map<String, Object>> vectorList = new ArrayList<>();
            for (Map.Entry<String, List<Double>> entry : vectors.entrySet()) {
                String id = entry.getKey();
                List<Double> embedding = entry.getValue();
                
                Map<String, Object> vector = new HashMap<>();
                vector.put("id", id);
                vector.put("values", embedding);
                
                if (metadata != null && metadata.containsKey(id)) {
                    vector.put("metadata", metadata.get(id));
                }
                
                vectorList.add(vector);
            }
            
            // Build request
            Map<String, Object> request = Map.of(
                "vectors", vectorList
            );
            
            // Call Pinecone API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully batch stored {} vectors", vectors.size());
            } else {
                logger.warn("Unexpected response status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to batch store vectors in Pinecone", e);
            throw new RuntimeException("Failed to batch store vectors in Pinecone", e);
        }
    }
    
    /**
     * Delete all vectors matching a filter.
     * 
     * @param filter Metadata filter for deletion
     */
    public void deleteByFilter(Map<String, Object> filter) {
        logger.debug("Deleting vectors by filter in Pinecone");
        
        try {
            String url = baseUrl + "/vectors/delete";
            
            // Build request
            Map<String, Object> request = Map.of(
                "filter", filter,
                "deleteAll", false
            );
            
            // Call Pinecone API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully deleted vectors by filter");
            } else {
                logger.warn("Unexpected response status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to delete vectors by filter in Pinecone", e);
            throw new RuntimeException("Failed to delete vectors by filter in Pinecone", e);
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
