package com.suhas.codeAnalyzer.service.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object for LLM requests, containing metadata and configuration
 */
public class LLMContext {
    private final LLMUseCase useCase;
    private final Map<String, Object> metadata;
    private final String sessionId;
    private final String userId;
    
    private LLMContext(Builder builder) {
        this.useCase = builder.useCase;
        this.metadata = new HashMap<>(builder.metadata);
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
    }
    
    public LLMUseCase getUseCase() {
        return useCase;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public static Builder builder(LLMUseCase useCase) {
        return new Builder(useCase);
    }
    
    public static class Builder {
        private final LLMUseCase useCase;
        private final Map<String, Object> metadata = new HashMap<>();
        private String sessionId;
        private String userId;
        
        private Builder(LLMUseCase useCase) {
            this.useCase = useCase;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public LLMContext build() {
            return new LLMContext(this);
        }
    }
}