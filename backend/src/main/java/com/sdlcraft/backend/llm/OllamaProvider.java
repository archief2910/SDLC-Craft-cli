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
 * Ollama LLM Provider implementation.
 * Uses locally running Ollama for AI completions - no API key required!
 */
@Component
@Primary
public class OllamaProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaProvider.class);
    
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;
    private boolean available = false;
    
    public OllamaProvider(
            @Value("${sdlcraft.llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${sdlcraft.llm.ollama.model:llama3.2}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        
        // Configure RestTemplate with timeouts (longer for local inference)
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(10000); // 10 seconds to connect
            setReadTimeout(300000); // 5 minutes for response (first request loads model into memory)
        }});
        
        // Check if Ollama is running
        checkAvailability();
    }
    
    private void checkAvailability() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                this.available = true;
                logger.info("✅ Ollama is running at {} with model: {}", baseUrl, model);
            }
        } catch (Exception e) {
            this.available = false;
            logger.warn("⚠️ Ollama not available at {} - make sure Ollama is running! Error: {}", baseUrl, e.getMessage());
        }
    }
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        if (!isAvailable()) {
            // Re-check availability in case Ollama was started after the app
            checkAvailability();
            if (!isAvailable()) {
                throw new LLMException("Ollama is not running. Please start Ollama first: 'ollama serve'");
            }
        }
        
        logger.debug("Requesting completion from Ollama (model: {})", model);
        
        try {
            double temperature = getDoubleParam(parameters, "temperature", 0.7);
            
            String url = baseUrl + "/api/chat";
            
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                "stream", false,
                "options", Map.of(
                    "temperature", temperature
                )
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new LLMException("Empty response from Ollama");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) body.get("message");
            
            if (message == null) {
                throw new LLMException("No message in Ollama response");
            }
            
            String content = (String) message.get("content");
            
            logger.debug("Received completion from Ollama");
            return content;
            
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Ollama API call failed", e);
            throw new LLMException("Ollama completion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<double[]> embed(List<String> texts) {
        logger.debug("Generating embeddings for {} texts using Ollama", texts.size());
        
        try {
            String url = baseUrl + "/api/embeddings";
            List<double[]> result = new ArrayList<>();
            
            // Ollama processes embeddings one at a time
            for (String text : texts) {
                Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", text
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
                
                @SuppressWarnings("unchecked")
                List<Number> embedding = (List<Number>) response.getBody().get("embedding");
                
                if (embedding != null) {
                    double[] embeddingArray = new double[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        embeddingArray[i] = embedding.get(i).doubleValue();
                    }
                    result.add(embeddingArray);
                }
            }
            
            logger.debug("Generated {} embeddings via Ollama", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("Ollama embedding failed, falling back to Hugging Face", e);
            return fallbackEmbeddings(texts);
        }
    }
    
    private List<double[]> fallbackEmbeddings(List<String> texts) {
        // Fallback to Hugging Face for embeddings if Ollama fails
        try {
            String url = "https://api-inference.huggingface.co/pipeline/feature-extraction/sentence-transformers/all-MiniLM-L6-v2";
            
            Map<String, Object> request = Map.of(
                "inputs", texts,
                "options", Map.of("wait_for_model", true)
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<List> response = restTemplate.postForEntity(url, entity, List.class);
            
            @SuppressWarnings("unchecked")
            List<List<Number>> embeddings = response.getBody();
            
            List<double[]> result = new ArrayList<>();
            if (embeddings != null) {
                for (List<Number> embedding : embeddings) {
                    double[] embeddingArray = new double[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        embeddingArray[i] = embedding.get(i).doubleValue();
                    }
                    result.add(embeddingArray);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Fallback embedding also failed", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null) return defaultValue;
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
}

