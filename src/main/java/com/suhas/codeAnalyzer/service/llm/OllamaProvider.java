package com.suhas.codeAnalyzer.service.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Ollama LLM Provider for local LLM integration
 */
@Component
public class OllamaProvider implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaProvider.class);
    
    @Value("${external.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${external.ollama.llm-model:codellama:7b-instruct}")
    private String llmModel;
    
    @Value("${external.ollama.timeout:120s}")
    private Duration timeout;
    
    private final WebClient webClient;
    
    public OllamaProvider() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String userPrompt, String systemPrompt, LLMContext context) {
        // Ollama uses combined prompt approach
        String combinedPrompt = buildCombinedPrompt(systemPrompt, userPrompt);
        return generateResponse(combinedPrompt);
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String userPrompt) {
        GenerateRequest request = new GenerateRequest(llmModel, userPrompt);
        
        logger.debug("Generating Ollama response for prompt length: {}", userPrompt.length());
        
        return webClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(5))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest)))
                .map(GenerateResponse::getResponse)
                .doOnSuccess(response -> logger.debug("Ollama response generated (length: {} chars)", 
                    response != null ? response.length() : 0))
                .doOnError(error -> logger.error("Failed to generate Ollama response: {}", error.getMessage()))
                .onErrorReturn("Sorry, I encountered an error while analyzing the code. Please try again.")
                .toFuture();
    }
    
    @Override
    public CompletableFuture<Boolean> isHealthy() {
        GenerateRequest request = new GenerateRequest(llmModel, "Hello");
        request.getOptions().setNum_predict(10);
        
        return webClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getResponse() != null && !response.getResponse().isEmpty())
                .doOnSuccess(healthy -> logger.debug("Ollama health check: {}", healthy ? "healthy" : "unhealthy"))
                .doOnError(error -> logger.warn("Ollama health check failed: {}", error.getMessage()))
                .onErrorReturn(false)
                .toFuture();
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public String getModelInfo() {
        return String.format("Model: %s, Service: %s", llmModel, ollamaBaseUrl);
    }
    
    @Override
    public boolean supportsUseCase(LLMUseCase useCase) {
        // Ollama supports all use cases but is preferred for analysis
        return true;
    }
    
    private String buildCombinedPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            return userPrompt;
        }
        return systemPrompt + "\n\n" + userPrompt;
    }
    
    // DTOs for Ollama API
    public static class GenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;
        private GenerateOptions options;
        
        public GenerateRequest(String model, String prompt) {
            this.model = model;
            this.prompt = prompt;
            this.stream = false;
            this.options = new GenerateOptions();
        }
        
        // Getters and setters
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
        public GenerateOptions getOptions() { return options; }
        public void setOptions(GenerateOptions options) { this.options = options; }
        
        public static class GenerateOptions {
            private double temperature = 0.1;
            private int num_predict = 500;
            private int top_k = 10;
            private double top_p = 0.9;
            
            // Getters and setters
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
            public int getNum_predict() { return num_predict; }
            public void setNum_predict(int num_predict) { this.num_predict = num_predict; }
            public int getTop_k() { return top_k; }
            public void setTop_k(int top_k) { this.top_k = top_k; }
            public double getTop_p() { return top_p; }
            public void setTop_p(double top_p) { this.top_p = top_p; }
        }
    }
    
    public static class GenerateResponse {
        private String response;
        private boolean done;
        private int[] context;
        private long total_duration;
        private long load_duration;
        private long prompt_eval_count;
        private long prompt_eval_duration;
        private long eval_count;
        private long eval_duration;
        
        // Getters and setters
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
        public int[] getContext() { return context; }
        public void setContext(int[] context) { this.context = context; }
        public long getTotal_duration() { return total_duration; }
        public void setTotal_duration(long total_duration) { this.total_duration = total_duration; }
        public long getLoad_duration() { return load_duration; }
        public void setLoad_duration(long load_duration) { this.load_duration = load_duration; }
        public long getPrompt_eval_count() { return prompt_eval_count; }
        public void setPrompt_eval_count(long prompt_eval_count) { this.prompt_eval_count = prompt_eval_count; }
        public long getPrompt_eval_duration() { return prompt_eval_duration; }
        public void setPrompt_eval_duration(long prompt_eval_duration) { this.prompt_eval_duration = prompt_eval_duration; }
        public long getEval_count() { return eval_count; }
        public void setEval_count(long eval_count) { this.eval_count = eval_count; }
        public long getEval_duration() { return eval_duration; }
        public void setEval_duration(long eval_duration) { this.eval_duration = eval_duration; }
    }
}