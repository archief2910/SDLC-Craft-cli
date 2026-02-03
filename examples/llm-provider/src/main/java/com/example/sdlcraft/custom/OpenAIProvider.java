package com.example.sdlcraft.custom;

import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.llm.LLMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI LLM Provider implementation.
 * 
 * Integrates with OpenAI's API for:
 * - Text completion (GPT-4, GPT-3.5-turbo)
 * - Text embeddings (text-embedding-ada-002)
 * 
 * Requires OPENAI_API_KEY environment variable.
 * 
 * @author SDLCraft Team
 * @since 1.0.0
 */
@Component
public class OpenAIProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";
    
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    
    public OpenAIProvider(
            @Value("${sdlcraft.llm.openai.api-key}") String apiKey,
            @Value("${sdlcraft.llm.openai.model:gpt-4}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = new RestTemplate();
        
        logger.info("Initialized OpenAI provider with model: {}", model);
    }
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        logger.debug("Requesting completion from OpenAI");
        
        try {
            // Extract parameters with defaults
            double temperature = getDoubleParam(parameters, "temperature", 0.7);
            int maxTokens = getIntParam(parameters, "max_tokens", 500);
            
            // Build request
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", 
                        "You are a helpful assistant for SDLC operations."),
                    Map.of("role", "user", "content", prompt)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
            );
            
            // Call OpenAI API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                COMPLETIONS_URL,
                entity,
                Map.class
            );
            
            // Extract completion
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from OpenAI");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = 
                (List<Map<String, Object>>) body.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new LLMException("No choices in OpenAI response");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = 
                (Map<String, Object>) choices.get(0).get("message");
            
            String content = (String) message.get("content");
            
            logger.debug("Received completion from OpenAI ({} tokens)", 
                ((Map<?, ?>) body.get("usage")).get("total_tokens"));
            
            return content;
            
        } catch (Exception e) {
            logger.error("OpenAI API call failed", e);
            throw new LLMException("OpenAI completion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Double> embed(List<String> texts) {
        logger.debug("Requesting embeddings from OpenAI for {} texts", texts.size());
        
        try {
            // Build request
            Map<String, Object> request = Map.of(
                "model", "text-embedding-ada-002",
                "input", texts
            );
            
            // Call OpenAI API
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                EMBEDDINGS_URL,
                entity,
                Map.class
            );
            
            // Extract embeddings
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from OpenAI");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = 
                (List<Map<String, Object>>) body.get("data");
            
            if (data == null || data.isEmpty()) {
                throw new LLMException("No embeddings in OpenAI response");
            }
            
            // Flatten all embeddings into a single list
            List<Double> allEmbeddings = data.stream()
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) item.get("embedding");
                    return embedding;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
            
            logger.debug("Received {} embeddings from OpenAI", data.size());
            
            return allEmbeddings;
            
        } catch (Exception e) {
            logger.error("OpenAI embedding call failed", e);
            throw new LLMException("OpenAI embedding failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
