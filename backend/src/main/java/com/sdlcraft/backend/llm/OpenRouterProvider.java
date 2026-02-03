package com.sdlcraft.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter LLM Provider implementation.
 * OpenRouter provides access to multiple AI models through a unified API.
 */
@Component
@Primary
public class OpenRouterProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterProvider.class);
    private static final String API_BASE = "https://openrouter.ai/api/v1";
    
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    
    public OpenRouterProvider(
            @Value("${sdlcraft.llm.openrouter.api-key:}") String apiKey,
            @Value("${sdlcraft.llm.openrouter.model:xiaomi/mimo-v2-flash:free}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        
        // Configure RestTemplate with timeouts
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(10000); // 10 seconds
            setReadTimeout(30000); // 30 seconds
        }});
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OpenRouter API key not configured - provider will not be available");
        } else {
            logger.info("Initialized OpenRouter provider with model: {}", model);
        }
    }
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        if (!isAvailable()) {
            logger.warn("OpenRouter provider not available - API key not configured");
            throw new LLMException("OpenRouter API key not configured");
        }
        
        logger.debug("Requesting completion from OpenRouter");
        
        try {
            double temperature = getDoubleParam(parameters, "temperature", 0.7);
            int maxTokens = getIntParam(parameters, "max_tokens", 500);
            
            String url = API_BASE + "/chat/completions";
            
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer", "https://github.com/sdlcraft/sdlcraft-cli");
            headers.set("X-Title", "SDLCraft CLI");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from OpenRouter");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new LLMException("No choices in OpenRouter response");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            
            String content = (String) message.get("content");
            
            logger.debug("Received completion from OpenRouter");
            return content;
            
        } catch (Exception e) {
            logger.error("OpenRouter API call failed", e);
            throw new LLMException("OpenRouter completion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<double[]> embed(List<String> texts) {
        logger.debug("Generating embeddings for {} texts using Pinecone Inference", texts.size());
        
        try {
            // Use Pinecone's inference API for embeddings
            String pineconeApiKey = System.getenv("PINECONE_API_KEY");
            if (pineconeApiKey == null || pineconeApiKey.isEmpty()) {
                throw new LLMException("Pinecone API key not configured for embeddings");
            }
            
            String url = "https://api.pinecone.io/embed";
            
            Map<String, Object> request = Map.of(
                "model", "multilingual-e5-large",
                "inputs", texts.stream().map(t -> Map.of("text", t)).toList(),
                "parameters", Map.of("input_type", "passage", "truncate", "END")
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Api-Key", pineconeApiKey);
            headers.set("X-Pinecone-API-Version", "2024-10");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from Pinecone Inference");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data == null || data.isEmpty()) {
                throw new LLMException("No embeddings in Pinecone response");
            }
            
            List<double[]> result = new ArrayList<>();
            for (Map<String, Object> item : data) {
                @SuppressWarnings("unchecked")
                List<Number> values = (List<Number>) item.get("values");
                double[] embeddingArray = new double[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    embeddingArray[i] = values.get(i).doubleValue();
                }
                result.add(embeddingArray);
            }
            
            logger.debug("Generated {} embeddings via Pinecone", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("Pinecone embedding API call failed", e);
            throw new LLMException("Pinecone embedding failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return "openrouter";
    }
    
    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null) return defaultValue;
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null) return defaultValue;
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
