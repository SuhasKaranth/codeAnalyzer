package com.suhas.codeAnalyzer.service.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Factory for creating appropriate LLM providers based on use case and configuration
 * Implements Strategy Pattern for extensible LLM provider selection
 */
@Service
public class LLMProviderFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMProviderFactory.class);
    
    // Provider configuration
    @Value("${llm.providers.default:groq}")
    private String defaultProvider;
    
    @Value("${llm.providers.analysis:ollama}")
    private String analysisProvider;
    
    @Value("${llm.providers.query:groq}")
    private String queryProvider;
    
    @Value("${llm.providers.chat:groq}")
    private String chatProvider;
    
    @Value("${llm.providers.fallback:ollama}")
    private String fallbackProvider;
    
    // Injected providers
    @Autowired
    private OllamaProvider ollamaProvider;
    
    @Autowired
    private GroqProvider groqProvider;
    
    // Provider registry
    private final Map<String, LLMProvider> providers = new HashMap<>();
    private final Map<LLMUseCase, String> useCaseProviders = new HashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Register providers
        providers.put("ollama", ollamaProvider);
        providers.put("groq", groqProvider);
        
        // Map use cases to providers
        useCaseProviders.put(LLMUseCase.ANALYSIS, analysisProvider);
        useCaseProviders.put(LLMUseCase.QUERY, queryProvider);
        useCaseProviders.put(LLMUseCase.CHAT, chatProvider);
        useCaseProviders.put(LLMUseCase.CODE_EXPLANATION, queryProvider);
        useCaseProviders.put(LLMUseCase.API_ANALYSIS, queryProvider);
        useCaseProviders.put(LLMUseCase.BUSINESS_LOGIC, queryProvider);
        
        logConfiguration();
    }
    
    /**
     * Get provider for specific use case
     * @param useCase The use case requiring LLM provider
     * @return LLMProvider instance
     */
    public LLMProvider getProvider(LLMUseCase useCase) {
        String providerName = useCaseProviders.getOrDefault(useCase, defaultProvider);
        return getProviderByName(providerName, useCase);
    }
    
    /**
     * Get provider by name with fallback logic
     * @param providerName Name of the provider
     * @param useCase Use case for context
     * @return LLMProvider instance
     */
    public LLMProvider getProviderByName(String providerName, LLMUseCase useCase) {
        LLMProvider provider = providers.get(providerName.trim().toLowerCase());
        
        if (provider != null && provider.supportsUseCase(useCase)) {
            logger.debug("Selected {} provider for use case: {}", providerName, useCase);
            return provider;
        }
        
        // Fallback logic
        logger.warn("Provider '{}' not available or doesn't support use case: {}. Using fallback.", 
                providerName, useCase);
        
        // For analysis, always use Ollama as fallback
        if (useCase.requiresLocalLLM()) {
            return ollamaProvider;
        }
        
        // For other use cases, try fallback provider
        LLMProvider fallback = providers.get(fallbackProvider.trim().toLowerCase());
        if (fallback != null) {
            return fallback;
        }
        
        // Last resort: return Ollama
        return ollamaProvider;
    }
    
    /**
     * Get provider by name (without use case context)
     * @param providerName Name of the provider
     * @return LLMProvider instance or null if not found
     */
    public LLMProvider getProviderByName(String providerName) {
        return providers.get(providerName.trim().toLowerCase());
    }
    
    /**
     * Get all available providers
     * @return Map of provider names to LLMProvider instances
     */
    public Map<String, LLMProvider> getAllProviders() {
        return new HashMap<>(providers);
    }
    
    /**
     * Check if provider is available
     * @param providerName Name of the provider
     * @return true if provider is available, false otherwise
     */
    public boolean isProviderAvailable(String providerName) {
        return providers.containsKey(providerName.trim().toLowerCase());
    }
    
    /**
     * Register a new provider (for extensibility)
     * @param name Provider name
     * @param provider LLMProvider instance
     */
    public void registerProvider(String name, LLMProvider provider) {
        providers.put(name.trim().toLowerCase(), provider);
        logger.info("Registered new LLM provider: {}", name);
    }
    
    /**
     * Get provider configuration for a use case
     * @param useCase The use case
     * @return Provider name configured for this use case
     */
    public String getConfiguredProvider(LLMUseCase useCase) {
        return useCaseProviders.getOrDefault(useCase, defaultProvider);
    }
    
    private void logConfiguration() {
        logger.info("=== LLM Provider Factory Configuration ===");
        logger.info("Default provider: {}", defaultProvider);
        logger.info("Analysis provider: {} (always local)", analysisProvider);
        logger.info("Query provider: {}", queryProvider);
        logger.info("Chat provider: {}", chatProvider);
        logger.info("Fallback provider: {}", fallbackProvider);
        logger.info("Available providers: {}", providers.keySet());
        logger.info("==========================================");
    }
}