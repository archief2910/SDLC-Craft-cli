package com.sdlcraft.backend.memory;

import com.sdlcraft.backend.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pinecone Vector Store implementation for production RAG.
 * 
 * Uses Pinecone's serverless vector database for semantic search over codebase.
 * Requires PINECONE_API_KEY environment variable to be set.
 * 
 * Features:
 * - Real vector embeddings via LLM provider
 * - Semantic similarity search
 * - Metadata filtering
 * - Namespace support for multi-project isolation
 */
@Component
@Primary
@ConditionalOnProperty(name = "sdlcraft.vector-store.pinecone.api-key")
public class PineconeVectorStore implements VectorStore {
    
    private static final Logger logger = LoggerFactory.getLogger(PineconeVectorStore.class);
    private static final int EMBEDDING_DIMENSION = 1024; // multilingual-e5-large dimension (Pinecone inference)
    
    private final String apiKey;
    private final String indexName;
    private final String environment;
    private final LLMProvider llmProvider;
    private final RestTemplate restTemplate;
    private String indexHost;
    
    public PineconeVectorStore(
            @Value("${sdlcraft.vector-store.pinecone.api-key:}") String apiKey,
            @Value("${sdlcraft.vector-store.pinecone.index:sdlcraft-index}") String indexName,
            @Value("${sdlcraft.vector-store.pinecone.environment:us-east-1}") String environment,
            LLMProvider llmProvider) {
        this.apiKey = apiKey;
        this.indexName = indexName;
        this.environment = environment;
        this.llmProvider = llmProvider;
        this.restTemplate = new RestTemplate();
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Pinecone API key not configured - vector store will not be available");
        } else {
            logger.info("Initializing Pinecone vector store with index: {}", indexName);
            initializeIndex();
        }
    }
    
    /**
     * Initialize or connect to Pinecone index.
     */
    private void initializeIndex() {
        try {
            // Get index host from Pinecone API
            String controllerUrl = "https://api.pinecone.io/indexes/" + indexName;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        controllerUrl, HttpMethod.GET, entity, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    this.indexHost = (String) body.get("host");
                    
                    // Check dimension compatibility
                    Integer existingDimension = (Integer) body.get("dimension");
                    if (existingDimension != null && existingDimension != EMBEDDING_DIMENSION) {
                        logger.warn("Index dimension mismatch: existing={}, required={}. Recreating index...", 
                                existingDimension, EMBEDDING_DIMENSION);
                        deleteAndRecreateIndex();
                    } else {
                        logger.info("Connected to Pinecone index: {} at {}", indexName, indexHost);
                    }
                }
            } catch (Exception e) {
                // Index might not exist, try to create it
                logger.info("Index not found, attempting to create: {}", indexName);
                createIndex();
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize Pinecone index", e);
        }
    }
    
    /**
     * Delete and recreate Pinecone index with correct dimensions.
     */
    private void deleteAndRecreateIndex() {
        try {
            logger.info("Deleting index: {}", indexName);
            String deleteUrl = "https://api.pinecone.io/indexes/" + indexName;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, Void.class);
            
            // Wait for deletion to complete
            logger.info("Waiting for index deletion to complete...");
            Thread.sleep(10000);
            
            // Recreate index
            createIndex();
            
        } catch (Exception e) {
            logger.error("Failed to delete and recreate index", e);
        }
    }
    
    /**
     * Create a new Pinecone index.
     */
    private void createIndex() {
        try {
            String createUrl = "https://api.pinecone.io/indexes";
            
            Map<String, Object> request = Map.of(
                    "name", indexName,
                    "dimension", EMBEDDING_DIMENSION,
                    "metric", "cosine",
                    "spec", Map.of(
                            "serverless", Map.of(
                                    "cloud", "aws",
                                    "region", environment
                            )
                    )
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(createUrl, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                this.indexHost = (String) response.getBody().get("host");
                logger.info("Created Pinecone index: {} at {}", indexName, indexHost);
                
                // Wait for index to be ready
                Thread.sleep(5000);
            }
            
        } catch (Exception e) {
            logger.error("Failed to create Pinecone index", e);
        }
    }
    
    @Override
    public void store(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
        if (!isAvailable()) {
            logger.warn("Pinecone not available, skipping store");
            return;
        }
        
        try {
            String url = "https://" + indexHost + "/vectors/upsert";
            
            // Convert embedding to float array
            List<Float> values = embedding.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
            
            // Prepare metadata - Pinecone requires specific types
            Map<String, Object> pineconeMetadata = new HashMap<>();
            pineconeMetadata.put("content", content);
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    Object value = entry.getValue();
                    // Pinecone supports string, number, boolean, and list of strings
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                        pineconeMetadata.put(entry.getKey(), value);
                    } else if (value instanceof List) {
                        pineconeMetadata.put(entry.getKey(), value);
                    } else if (value != null) {
                        pineconeMetadata.put(entry.getKey(), value.toString());
                    }
                }
            }
            
            Map<String, Object> vector = Map.of(
                    "id", id,
                    "values", values,
                    "metadata", pineconeMetadata
            );
            
            Map<String, Object> request = Map.of(
                    "vectors", List.of(vector),
                    "namespace", "default"
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            logger.debug("Stored vector: {}", id);
            
        } catch (Exception e) {
            logger.error("Failed to store vector in Pinecone: {}", id, e);
        }
    }
    
    @Override
    public void storeWithAutoEmbedding(String id, String content, Map<String, Object> metadata) {
        if (!isAvailable()) {
            logger.warn("Pinecone not available, skipping store");
            return;
        }
        
        try {
            // Generate embedding using LLM provider
            List<double[]> embeddings = llmProvider.embed(List.of(content));
            if (embeddings.isEmpty()) {
                logger.error("Failed to generate embedding for content");
                return;
            }
            
            // Convert double[] to List<Double>
            List<Double> embedding = Arrays.stream(embeddings.get(0))
                    .boxed()
                    .collect(Collectors.toList());
            
            store(id, content, embedding, metadata);
            
        } catch (Exception e) {
            logger.error("Failed to store with auto embedding: {}", id, e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(List<Double> queryEmbedding, int limit, Map<String, Object> filter) {
        if (!isAvailable()) {
            logger.warn("Pinecone not available, returning empty results");
            return Collections.emptyList();
        }
        
        try {
            String url = "https://" + indexHost + "/query";
            
            // Convert embedding to float array
            List<Float> values = queryEmbedding.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
            
            Map<String, Object> request = new HashMap<>();
            request.put("vector", values);
            request.put("topK", limit);
            request.put("includeMetadata", true);
            request.put("includeValues", true);
            request.put("namespace", "default");
            
            // Add filter if provided
            if (filter != null && !filter.isEmpty()) {
                Map<String, Object> pineconeFilter = new HashMap<>();
                for (Map.Entry<String, Object> entry : filter.entrySet()) {
                    pineconeFilter.put(entry.getKey(), Map.of("$eq", entry.getValue()));
                }
                request.put("filter", pineconeFilter);
            }
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseSearchResults(response.getBody());
            }
            
        } catch (Exception e) {
            logger.error("Failed to search Pinecone", e);
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public List<VectorSearchResult> searchByText(String queryText, int limit, Map<String, Object> filter) {
        if (!isAvailable()) {
            logger.warn("Pinecone not available, returning empty results");
            return Collections.emptyList();
        }
        
        try {
            // Generate embedding for query
            List<double[]> embeddings = llmProvider.embed(List.of(queryText));
            if (embeddings.isEmpty()) {
                logger.error("Failed to generate query embedding");
                return Collections.emptyList();
            }
            
            // Convert double[] to List<Double>
            List<Double> queryEmbedding = Arrays.stream(embeddings.get(0))
                    .boxed()
                    .collect(Collectors.toList());
            
            return search(queryEmbedding, limit, filter);
            
        } catch (Exception e) {
            logger.error("Failed to search by text", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean delete(String id) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            String url = "https://" + indexHost + "/vectors/delete";
            
            Map<String, Object> request = Map.of(
                    "ids", List.of(id),
                    "namespace", "default"
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            logger.debug("Deleted vector: {}", id);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to delete vector: {}", id, e);
            return false;
        }
    }
    
    @Override
    public int deleteByFilter(Map<String, Object> filter) {
        if (!isAvailable() || filter == null || filter.isEmpty()) {
            return 0;
        }
        
        try {
            String url = "https://" + indexHost + "/vectors/delete";
            
            // Build Pinecone filter
            Map<String, Object> pineconeFilter = new HashMap<>();
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                pineconeFilter.put(entry.getKey(), Map.of("$eq", entry.getValue()));
            }
            
            Map<String, Object> request = Map.of(
                    "filter", pineconeFilter,
                    "namespace", "default",
                    "deleteAll", false
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            logger.debug("Deleted vectors by filter");
            return 1; // Pinecone doesn't return count
            
        } catch (Exception e) {
            logger.error("Failed to delete by filter", e);
            return 0;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && indexHost != null;
    }
    
    @Override
    public String getImplementationName() {
        return "pinecone";
    }
    
    /**
     * Create HTTP headers with Pinecone API key.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        return headers;
    }
    
    /**
     * Parse Pinecone search results into VectorSearchResult objects.
     */
    @SuppressWarnings("unchecked")
    private List<VectorSearchResult> parseSearchResults(Map<String, Object> response) {
        List<VectorSearchResult> results = new ArrayList<>();
        
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.get("matches");
        if (matches == null) {
            return results;
        }
        
        for (Map<String, Object> match : matches) {
            String id = (String) match.get("id");
            Number score = (Number) match.get("score");
            Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
            List<Number> values = (List<Number>) match.get("values");
            
            String content = metadata != null ? (String) metadata.get("content") : "";
            
            List<Double> embedding = values != null
                    ? values.stream().map(Number::doubleValue).collect(Collectors.toList())
                    : Collections.emptyList();
            
            results.add(new VectorSearchResult(
                    id,
                    content,
                    score != null ? score.doubleValue() : 0.0,
                    embedding,
                    metadata
            ));
        }
        
        return results;
    }
}

