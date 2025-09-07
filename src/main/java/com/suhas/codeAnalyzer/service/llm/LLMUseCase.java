package com.suhas.codeAnalyzer.service.llm;

/**
 * Enumeration of different LLM use cases for context-aware provider selection
 */
public enum LLMUseCase {
    /**
     * Repository analysis - always uses local LLM (Ollama)
     * Used during code parsing and initial repository processing
     */
    ANALYSIS("analysis"),
    
    /**
     * User queries - configurable provider (default: Groq)
     * Used for answering user questions about code
     */
    QUERY("query"),
    
    /**
     * Chat responses - configurable provider (default: Groq)
     * Used for interactive chat conversations
     */
    CHAT("chat"),
    
    /**
     * Code explanation - configurable provider
     * Used for explaining specific code snippets
     */
    CODE_EXPLANATION("code-explanation"),
    
    /**
     * API analysis - configurable provider
     * Used for analyzing REST API endpoints
     */
    API_ANALYSIS("api-analysis"),
    
    /**
     * Business logic analysis - configurable provider
     * Used for analyzing business logic flows
     */
    BUSINESS_LOGIC("business-logic");
    
    private final String configKey;
    
    LLMUseCase(String configKey) {
        this.configKey = configKey;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    /**
     * Check if this use case should always use local LLM
     * @return true if must use local LLM, false if configurable
     */
    public boolean requiresLocalLLM() {
        return this == ANALYSIS;
    }
}