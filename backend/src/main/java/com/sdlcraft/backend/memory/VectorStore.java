package com.sdlcraft.backend.memory;

import java.util.List;
import java.util.Map;

/**
 * Vector store abstraction for semantic search.
 * 
 * Provides pluggable interface for different vector store implementations
 * (e.g., pgvector, Pinecone, Weaviate, Qdrant, etc.).
 * 
 * Design rationale:
 * - Interface allows swapping vector store implementations
 * - No hard dependency on specific vector database
 * - Supports both storage and similarity search
 * - Enables semantic retrieval of context and commands
 */
public interface VectorStore {
    
    /**
     * Store a document with its embedding.
     * 
     * @param id unique identifier for the document
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     */
    void store(String id, String content, List<Double> embedding, Map<String, Object> metadata);
    
    /**
     * Store a document and generate embedding automatically.
     * 
     * @param id unique identifier for the document
     * @param content the text content
     * @param metadata additional metadata
     */
    void storeWithAutoEmbedding(String id, String content, Map<String, Object> metadata);
    
    /**
     * Search for similar documents using vector similarity.
     * 
     * @param queryEmbedding the query vector
     * @param limit maximum number of results
     * @param filter optional metadata filter
     * @return list of similar documents with similarity scores
     */
    List<VectorSearchResult> search(List<Double> queryEmbedding, int limit, Map<String, Object> filter);
    
    /**
     * Search for similar documents using text query.
     * Automatically generates embedding from query text.
     * 
     * @param queryText the query text
     * @param limit maximum number of results
     * @param filter optional metadata filter
     * @return list of similar documents with similarity scores
     */
    List<VectorSearchResult> searchByText(String queryText, int limit, Map<String, Object> filter);
    
    /**
     * Delete a document by ID.
     * 
     * @param id the document ID to delete
     * @return true if deleted, false if not found
     */
    boolean delete(String id);
    
    /**
     * Delete all documents matching filter.
     * 
     * @param filter metadata filter
     * @return number of documents deleted
     */
    int deleteByFilter(Map<String, Object> filter);
    
    /**
     * Check if vector store is available and healthy.
     * 
     * @return true if available
     */
    boolean isAvailable();
    
    /**
     * Get the name of this vector store implementation.
     * 
     * @return implementation name (e.g., "pgvector", "pinecone")
     */
    String getImplementationName();
}
