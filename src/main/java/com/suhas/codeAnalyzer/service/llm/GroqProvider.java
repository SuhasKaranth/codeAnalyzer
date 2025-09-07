package com.suhas.codeAnalyzer.service.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Groq LLM Provider for cloud-based LLM integration
 */
@Component
public class GroqProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(GroqProvider.class);
    
    @Value("${groq.api.key:}")
    private String groqApiKey;
    
    @Value("${groq.api.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;
    
    @Value("${groq.api.model:llama-3.1-8b-instant}")
    private String groqModel;
    
    @Value("${groq.api.temperature:0.1}")
    private double groqTemperature;
    
    @Value("${groq.api.max-tokens:2048}")
    private int groqMaxTokens;
    
    // Default system prompts
    @Value("${groq.system-prompts.default:You are an expert Java and Spring Boot developer assistant.}")
    private String defaultSystemPrompt;
    
    @Override
    public CompletableFuture<String> generateResponse(String userPrompt, String systemPrompt, LLMContext context) {
        if (!isConfigured()) {
            logger.warn("Groq provider not properly configured - API key missing");
            return CompletableFuture.completedFuture("Groq API not configured. Please check your API key.");
        }
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Create request body for Groq API (OpenAI format)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", groqModel);
            requestBody.put("messages", Arrays.asList(
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : defaultSystemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("temperature", groqTemperature);
            requestBody.put("max_tokens", groqMaxTokens);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + groqApiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.debug("Sending request to Groq API for prompt length: {}", userPrompt.length());
            
            // Call Groq API
            ResponseEntity<Map> response = restTemplate.postForEntity(
                groqBaseUrl + "/chat/completions", entity, Map.class);
            
            // Extract response content
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    logger.debug("Groq API response received successfully (length: {} chars)", content.length());
                    return CompletableFuture.completedFuture(content);
                }
            }
            
            logger.error("Invalid response structure from Groq API");
            return CompletableFuture.completedFuture("Sorry, I received an invalid response from the API. Please try again.");
            
        } catch (Exception e) {
            logger.error("Failed to generate response with Groq API: {}", e.getMessage());
            return CompletableFuture.completedFuture("Sorry, I encountered an error while processing your request. Please try again.");
        }
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String userPrompt) {
        return generateResponse(userPrompt, defaultSystemPrompt, null);
    }
    
    @Override
    public CompletableFuture<Boolean> isHealthy() {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return generateResponse("Hello", "Respond with 'OK'", null)
                .thenApply(response -> response != null && response.contains("OK"))
                .exceptionally(throwable -> {
                    logger.warn("Groq health check failed: {}", throwable.getMessage());
                    return false;
                });
    }
    
    @Override
    public String getProviderName() {
        return "groq";
    }
    
    @Override
    public String getModelInfo() {
        return String.format("Model: %s, Service: %s", groqModel, groqBaseUrl);
    }
    
    @Override
    public boolean supportsUseCase(LLMUseCase useCase) {
        // Groq supports all use cases except analysis (which should use local LLM)
        return !useCase.requiresLocalLLM();
    }
    
    private boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.trim().isEmpty();
    }
}