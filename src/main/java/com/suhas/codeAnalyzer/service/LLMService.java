package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
public class LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);

    @Value("${external.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${external.ollama.llm-model}")
    private String llmModel;

    @Value("${external.ollama.timeout:120s}")  // Increased from 60s to 120s
    private Duration timeout;

    private final WebClient webClient;

    public LLMService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    // Request/Response DTOs for Ollama API
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
            private double temperature = 0.1; // Low temperature for more focused responses
            private int num_predict = 500;    // Max tokens to generate
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

    /**
     * Generate response using Code Llama with retrieved code context
     */
    public CompletableFuture<String> generateCodeAnalysisResponse(String userQuery, String codeContext) {
        String enhancedPrompt = buildCodeAnalysisPrompt(userQuery, codeContext);

        logger.debug("Generating LLM response for query: {}", userQuery);
        logger.debug("Context length: {} characters", codeContext.length());

        return generateResponse(enhancedPrompt)
                .thenApply(response -> {
                    logger.debug("Generated response length: {} characters", response.length());
                    return response;
                });
    }

    /**
     * Generate response for code explanation
     */
    public CompletableFuture<String> explainCode(String codeSnippet) {
        String prompt = buildCodeExplanationPrompt(codeSnippet);

        logger.debug("Generating code explanation for snippet length: {}", codeSnippet.length());

        return generateResponse(prompt);
    }

    /**
     * Generate response for API endpoint analysis
     */
    public CompletableFuture<String> analyzeApiEndpoints(String codeContext) {
        String prompt = buildApiAnalysisPrompt(codeContext);

        logger.debug("Analyzing API endpoints in context");

        return generateResponse(prompt);
    }

    /**
     * Generate response for business logic flow analysis
     */
    public CompletableFuture<String> analyzeBusinessLogic(String userQuery, String codeContext) {
        String prompt = buildBusinessLogicPrompt(userQuery, codeContext);

        logger.debug("Analyzing business logic flow");

        return generateResponse(prompt);
    }

    /**
     * Core method to generate response from Ollama
     */
    private CompletableFuture<String> generateResponse(String prompt) {
        GenerateRequest request = new GenerateRequest(llmModel, prompt);

        return webClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateResponse.class)
                .timeout(timeout)  // Now 120s
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(5))  // Add retry
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest)))
                .map(GenerateResponse::getResponse)
                .doOnError(error -> {
                    logger.error("Failed to generate LLM response: {}", error.getMessage());
                })
                .onErrorReturn("Sorry, I encountered an error while analyzing the code. Please try again.")
                .toFuture();
    }

    /**
     * Build prompt for general code analysis
     */
    private String buildCodeAnalysisPrompt(String userQuery, String codeContext) {
        return String.format("""
            You are an expert Java and Spring Boot developer. Analyze the following code and answer the user's question.
            
            **Code Context:**
            ```java
            %s
            ```
            
            **User Question:** %s
            
            **Instructions:**
            - Provide a clear, concise answer based on the code shown
            - Focus on practical implementation details
            - If the code shows Spring Boot patterns, explain them
            - If you see REST endpoints, describe what they do
            - If you see business logic, explain the flow
            - Keep your response under 300 words
            
            **Answer:**
            """, codeContext, userQuery);
    }

    /**
     * Build prompt for code explanation
     */
    private String buildCodeExplanationPrompt(String codeSnippet) {
        return String.format("""
            Explain this Java/Spring Boot code clearly and concisely:
            
            ```java
            %s
            ```
            
            Focus on:
            - What this code does
            - Key Spring Boot annotations and their purpose
            - Any REST endpoints or business logic
            - Dependencies and how they're used
            
            Keep the explanation under 200 words.
            """, codeSnippet);
    }

    /**
     * Enhanced API analysis prompt with specific code filtering instructions
     */
    private String buildApiAnalysisPrompt(String codeContext) {
        return String.format("""
                Analyze this Spring Boot code and extract ONLY the REST API endpoints.
                
                ```java
                %s
                ```
                
                RULES FOR ANALYSIS:
                1. Find classes with @RestController, @Controller, or @Path annotations
                2. Extract HTTP endpoints (@GetMapping, @PostMapping, @RequestMapping, etc.)
                3. IGNORE: @Service, @Repository, @Component, utility classes, main methods
                
                RULES FOR CODE SAMPLES:
                1. Show ONLY controller classes that contain REST endpoints
                2. Show ONLY the methods that have HTTP mapping annotations
                3. Do NOT include: service implementations, repositories, domain models, configuration classes
                4. If a class has no REST endpoints, do NOT include it in code samples
                5. Truncate long method bodies - show only the method signature and key annotations
                
                REQUIRED OUTPUT FORMAT:
                
                ## REST Endpoints Found:
                
                ### [ControllerName]
                **Base Path:** [class-level @RequestMapping path if any]
                        
                - **[HTTP_METHOD]** `[FULL_PATH]` → `[methodName]()` - [brief description]
                        
                ## Controller Code Samples:
                        
                **[ControllerName]:**
                ```java
                @RestController
                @RequestMapping("/base-path")  // only if present
                public class ControllerName {
                    
                    @GetMapping("/path")
                    public ReturnType methodName(Parameters...) {
                        // implementation details omitted
                    }
                    
                    // ... other endpoint methods only
                }
                ```
                        
                VALIDATION CHECKLIST:
                - [ ] Only show classes that have REST endpoints
                - [ ] Only show methods with @GetMapping, @PostMapping, etc.
                - [ ] Do not show service classes or repository classes in code samples
                - [ ] Keep method bodies short or omit them
                - [ ] Group by controller class
                        
                Be precise and focused - show only REST endpoint controllers and their HTTP mapping methods.
                """, codeContext);
    }

    /**
     * Build prompt for business logic analysis
     */
    private String buildBusinessLogicPrompt(String userQuery, String codeContext) {
        return String.format("""
            Analyze the business logic flow in this Spring Boot application:
            
            ```java
            %s
            ```
            
            **Question:** %s
            
            Focus on:
            - The sequence of operations
            - Service layer interactions
            - Database operations
            - External API calls
            - Error handling
            
            Provide a step-by-step explanation of the flow.
            """, codeContext, userQuery);
    }

    /**
     * Health check for LLM service
     */
    public CompletableFuture<Boolean> healthCheck() {
        GenerateRequest request = new GenerateRequest(llmModel, "Hello");
        request.getOptions().setNum_predict(10);

        return webClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getResponse() != null && !response.getResponse().isEmpty())
                .doOnSuccess(healthy -> logger.debug("LLM health check: {}", healthy ? "healthy" : "unhealthy"))
                .doOnError(error -> logger.warn("LLM health check failed: {}", error.getMessage()))
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Get LLM model info
     */
    public String getLLMModelInfo() {
        return String.format("Model: %s, Service: %s", llmModel, ollamaBaseUrl);
    }
}