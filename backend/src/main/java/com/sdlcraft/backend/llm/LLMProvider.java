package com.sdlcraft.backend.llm;

import java.util.List;
import java.util.Map;

/**
 * LLMProvider is an abstraction layer for Large Language Model operations.
 * 
 * This interface allows the system to work with different LLM providers (OpenAI, Anthropic,
 * local models, etc.) without hard dependencies. Implementations can be swapped at runtime
 * through dependency injection.
 * 
 * The abstraction ensures that the Intent Inference Service and other components don't
 * depend on specific LLM APIs, making the system more flexible and testable.
 * 
 * Requirements: 9.5
 */
public interface LLMProvider {
    
    /**
     * Generates a text completion based on the given prompt.
     * 
     * This method sends a prompt to the LLM and returns the generated text.
     * Parameters can be used to control generation (temperature, max_tokens, etc.).
     * 
     * @param prompt the input prompt
     * @param parameters optional parameters for generation (temperature, max_tokens, etc.)
     * @return the generated text completion
     * @throws LLMException if the LLM call fails
     */
    String complete(String prompt, Map<String, Object> parameters);
    
    /**
     * Generates embeddings for the given texts.
     * 
     * Embeddings are vector representations of text that can be used for semantic
     * similarity search. This is used by the long-term memory system to find
     * similar past commands.
     * 
     * @param texts the texts to embed
     * @return list of embedding vectors (one per input text)
     * @throws LLMException if the embedding call fails
     */
    List<double[]> embed(List<String> texts);
    
    /**
     * Checks if the LLM provider is available and healthy.
     * 
     * This can be used to determine if the system should fall back to template-based
     * inference when the LLM is unavailable.
     * 
     * @return true if the provider is available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Returns the name of the LLM provider.
     * 
     * @return provider name (e.g., "OpenAI", "Anthropic", "Local")
     */
    String getProviderName();
}
