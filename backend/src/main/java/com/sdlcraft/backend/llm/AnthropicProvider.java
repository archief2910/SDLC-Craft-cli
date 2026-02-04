package com.sdlcraft.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Anthropic Claude LLM Provider implementation.
 * Direct integration with Anthropic's Claude API.
 */
@Component
@Primary
@ConditionalOnProperty(name = "sdlcraft.llm.provider", havingValue = "anthropic", matchIfMissing = false)
public class AnthropicProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String API_BASE = "https://api.anthropic.com/v1";
    
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    
    public AnthropicProvider(
            @Value("${sdlcraft.llm.anthropic.api-key:}") String apiKey,
            @Value("${sdlcraft.llm.anthropic.model:claude-3-5-sonnet-20241022}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        
        // Configure RestTemplate with timeouts
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(10000); // 10 seconds
            setReadTimeout(120000); // 120 seconds for long generations
        }});
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Anthropic API key not configured - provider will not be available");
        } else {
            logger.info("Initialized Anthropic provider with model: {}", model);
        }
    }
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        if (!isAvailable()) {
            logger.warn("Anthropic provider not available - API key not configured");
            throw new LLMException("Anthropic API key not configured");
        }
        
        logger.debug("Requesting completion from Anthropic Claude");
        
        try {
            double temperature = getDoubleParam(parameters, "temperature", 0.7);
            int maxTokens = getIntParam(parameters, "max_tokens", 4096);
            
            String url = API_BASE + "/messages";
            
            Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                "temperature", temperature
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from Anthropic");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
            
            if (content == null || content.isEmpty()) {
                throw new LLMException("No content in Anthropic response");
            }
            
            // Get text from the first content block
            String text = (String) content.get(0).get("text");
            
            logger.debug("Received completion from Anthropic Claude");
            return text;
            
        } catch (Exception e) {
            logger.error("Anthropic API call failed", e);
            throw new LLMException("Anthropic completion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<double[]> embed(List<String> texts) {
        // Anthropic doesn't provide embeddings API, use Pinecone or throw
        logger.warn("Anthropic does not support embeddings - using Pinecone fallback");
        
        try {
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
            
            return result;
            
        } catch (Exception e) {
            logger.error("Embedding API call failed", e);
            throw new LLMException("Embedding failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return "anthropic";
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

