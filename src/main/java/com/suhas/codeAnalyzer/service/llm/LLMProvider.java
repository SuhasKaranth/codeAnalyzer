package com.suhas.codeAnalyzer.service.llm;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for different LLM providers (Groq, OpenAI, Ollama, etc.)
 * Enables extensible and configurable LLM integrations
 */
public interface LLMProvider {
    
    /**
     * Generate response using this LLM provider
     * @param userPrompt The user's input prompt
     * @param systemPrompt The system prompt for context
     * @param context Additional context for the request
     * @return CompletableFuture with the generated response
     */
    CompletableFuture<String> generateResponse(String userPrompt, String systemPrompt, LLMContext context);
    
    /**
     * Generate response with just user prompt (uses default system prompt)
     * @param userPrompt The user's input prompt
     * @return CompletableFuture with the generated response
     */
    CompletableFuture<String> generateResponse(String userPrompt);
    
    /**
     * Check if this provider is healthy and available
     * @return CompletableFuture<Boolean> indicating health status
     */
    CompletableFuture<Boolean> isHealthy();
    
    /**
     * Get the provider name for logging and identification
     * @return Provider name (e.g., "groq", "openai", "ollama")
     */
    String getProviderName();
    
    /**
     * Get provider-specific model information
     * @return Model information string
     */
    String getModelInfo();
    
    /**
     * Check if provider supports the given use case
     * @param useCase The use case to check
     * @return true if supported, false otherwise
     */
    boolean supportsUseCase(LLMUseCase useCase);
}