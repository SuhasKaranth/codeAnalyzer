package com.suhas.codeAnalyzer.chat.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.suhas.codeAnalyzer.chat.model.*;
import com.suhas.codeAnalyzer.chat.ollama.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ChatLLMService {

    private static final Logger log = LoggerFactory.getLogger(ChatLLMService.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2:1b}")
    private String modelName;

    @Value("${slm.intent.model:phi3:3.8b}")
    private String slmModelName;

    @Value("${slm.intent.temperature:0.1}")
    private double slmTemperature;

    @Value("${slm.intent.enabled:true}")
    private boolean slmIntentEnabled;

    @Value("${chat.use-pattern-matching:true}")
    private boolean usePatternMatching;

    @Value("${chat.force-pattern-matching:false}")
    private boolean forcePatternMatching;

    @Value("${server.port:8080}")
    private String serverPort;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    // Pattern for GitHub URLs
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "https?://github\\.com/[\\w.-]+/[\\w.-]+", Pattern.CASE_INSENSITIVE
    );

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.registerModule(new JavaTimeModule());

        // TEMPORARY: Force the configuration for testing
        this.forcePatternMatching = false;
        this.usePatternMatching = true;

        log.info("ChatLLMService initialized:");
        log.info("  Main model: {}", modelName);
        log.info("  SLM model: {}", slmModelName);
        log.info("  SLM intent analysis: {}", slmIntentEnabled);
        log.info("  Pattern matching: {}", usePatternMatching);
        log.info("  Force pattern matching: {}", forcePatternMatching);
        log.info("  Ollama URL: {}", ollamaBaseUrl);
        log.info("  Server port: {}", serverPort);
    }

    public LLMResponse callWithContext(String userMessage, ConversationContext context) {
        log.info("=== Processing message: '{}' ===", userMessage);
        log.info("Session context - Repository: {}", context.getCurrentRepository());
        log.info("Configuration - usePatternMatching: {}, forcePatternMatching: {}", usePatternMatching, forcePatternMatching);

        // PRIORITY 1: Try SLM intent analysis first (if enabled)
        String slmIntent = null;
        if (slmIntentEnabled) {
            slmIntent = analyzePotentialIntentWithSLM(userMessage, context);
            if (slmIntent != null && !slmIntent.equals("UNKNOWN")) {
                log.info("🧠 SLM detected intent: {}", slmIntent);
                LLMResponse slmResponse = createResponseFromIntent(slmIntent, userMessage, context);
                if (slmResponse != null) {
                    log.info("✅ Used SLM intent analysis - Action: {}", slmResponse.getAction());
                    return slmResponse;
                }
            }
        } else {
            log.info("🚫 SLM intent analysis disabled, skipping");
        }

        // PRIORITY 2: Fallback to pattern matching if SLM fails and pattern matching is enabled
        if (usePatternMatching && (slmIntent == null || slmIntent.equals("UNKNOWN"))) {
            log.info("🔄 SLM intent unclear, trying pattern matching fallback");
            LLMResponse patternResponse = tryPatternMatching(userMessage, context);
            if (patternResponse != null) {
                log.info("✅ Used pattern matching fallback - Action: {}", patternResponse.getAction());
                return patternResponse;
            }
        }

        // If force pattern matching is enabled, don't call other APIs
        if (forcePatternMatching) {
            log.info("🔒 Force pattern matching enabled, creating fallback response");
            return createGenericHelpResponse(userMessage);
        }

        // Check if we have a repository to query against
        if (context.getCurrentRepository() != null) {
            log.info("🔍 No clear intent detected, trying Query API for repository-specific question");
            return callQueryAPI(userMessage, context);
        }

        // For repository-specific questions without context, suggest analyzing first
        if (isRepositorySpecificQuestion(userMessage)) {
            log.info("📝 Repository-specific question without context, suggesting analysis");
            return createAnalysisNeededResponse(userMessage);
        }

        // Fall back to direct LLM for general questions
        log.info("🤖 Falling back to direct LLM for general query");
        return callDirectLLM(userMessage, context);
    }

    private LLMResponse tryPatternMatching(String userMessage, ConversationContext context) {
        String message = userMessage.toLowerCase().trim();
        log.info("🔍 Pattern matching for: '{}'", message);

        // PRIORITY 1: Status/Progress queries (even with GitHub URL)
        if (containsAny(message, "status", "progress", "complete", "finished", "done", "ready")) {
            log.info("✅ Detected status inquiry");
            return createStatusResponse(userMessage, context);
        }

        // PRIORITY 2: Greeting patterns
        if (containsAny(message, "hello", "hi", "hey", "what can you", "help me", "what do you do")) {
            log.info("✅ Detected greeting pattern");
            return createGreetingResponse();
        }

        // PRIORITY 3: Endpoint patterns (but not if asking about analysis)
        if (containsAny(message, "endpoint", "api", "rest", "route") &&
                !containsAny(message, "analyze", "analysis", "status", "progress")) {
            log.info("✅ Detected endpoint pattern");
            return createFindEndpointsResponse();
        }

        // PRIORITY 4: Component patterns (but not if asking about analysis)
        if (containsAny(message, "component", "service", "spring", "bean") &&
                !containsAny(message, "analyze", "analysis", "status", "progress")) {
            log.info("✅ Detected component pattern");
            return createFindComponentsResponse();
        }

        // PRIORITY 5: Health check patterns
        if (containsAny(message, "health", "check", "working") &&
                !GITHUB_URL_PATTERN.matcher(userMessage).find()) {
            log.info("✅ Detected health pattern");
            return createHealthResponse();
        }

        // PRIORITY 6: File listing patterns (but not if asking about analysis)
        if (containsAny(message, "file", "structure", "list", "directory") &&
                !containsAny(message, "analyze", "analysis", "status", "progress")) {
            log.info("✅ Detected file listing pattern");
            return createListFilesResponse();
        }

        // PRIORITY 7: Search patterns (but not if asking about analysis)
        if (containsAny(message, "search", "find", "look for") &&
                !containsAny(message, "analyze", "analysis", "status", "progress")) {
            log.info("✅ Detected search pattern");
            String query = extractSearchQuery(userMessage);
            return createSearchResponse(query);
        }

        // PRIORITY 8: GitHub URL detection for ANALYSIS (only if explicitly asking to analyze)
        if (GITHUB_URL_PATTERN.matcher(userMessage).find() &&
                containsAny(message, "analyze", "analysis", "process", "start", "begin") &&
                !containsAny(message, "explain", "what does", "describe", "detail", "specific file")) {
            String repoUrl = extractGitHubUrl(userMessage);
            log.info("✅ Detected GitHub URL for analysis: {}", repoUrl);
            return createAnalyzeRepoResponse(repoUrl);
        }
        
        // PRIORITY 9: Detailed code explanation requests (when repository is already analyzed)
        if (containsAny(message, "explain", "what does", "describe", "detail") &&
                (message.contains(".java") || message.contains(".kt") || message.contains(".groovy") || 
                 message.contains("class") || message.contains("file") || message.contains("method"))) {
            log.info("✅ Detected detailed code explanation request");
            if (context.getCurrentRepository() != null) {
                return callQueryAPI(userMessage, context);
            } else {
                return createAnalysisNeededResponse(userMessage);
            }
        }

        log.info("❌ No pattern matched for: '{}'", message);
        return null;
    }

    /**
     * NEW: SLM-based intent analysis using phi3:3.8b
     */
    public String analyzePotentialIntentWithSLM(String userMessage, ConversationContext context) {
        try {
            log.info("🧠 Analyzing intent with SLM: '{}'", userMessage);
            long startTime = System.currentTimeMillis();

            String intentPrompt = buildIntentAnalysisPrompt(userMessage, context);
            log.debug("📝 Intent analysis prompt: {}", intentPrompt);

            List<OllamaMessage> messages = new ArrayList<>();
            messages.add(OllamaMessage.system(getIntentAnalysisSystemPrompt()));
            messages.add(OllamaMessage.user(intentPrompt));

            OllamaRequest request = OllamaRequest.builder()
                    .model(slmModelName)
                    .messages(messages)
                    .temperature(slmTemperature)
                    .build();

            OllamaResponse ollamaResponse = restTemplate.postForObject(
                    ollamaBaseUrl + "/api/chat",
                    request,
                    OllamaResponse.class
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ SLM intent analysis completed in {} ms", duration);

            if (ollamaResponse != null && ollamaResponse.getMessage() != null) {
                String intentResponse = ollamaResponse.getMessage().getContent().trim();
                log.info("🧠 SLM Intent Analysis Result: {}", intentResponse);
                
                // Extract the action from the response
                String extractedAction = extractActionFromSLMResponse(intentResponse);
                log.info("🎯 Extracted Action: {}", extractedAction);
                
                return extractedAction;
            } else {
                log.error("❌ Invalid response from SLM for intent analysis");
                return null;
            }

        } catch (Exception e) {
            log.error("❌ Error during SLM intent analysis", e);
            return null;
        }
    }

    private String getIntentAnalysisSystemPrompt() {
        return """
            You are an intent classifier for a code analysis assistant.
            Analyze the user's message and determine their primary intent.
            
            Available actions:
            - ANALYZE_REPO: User wants to clone/analyze a NEW GitHub repository (contains GitHub URL and words like "analyze", "clone")
            - GET_STATUS: User wants to check analysis progress or status (contains "status", "progress", "complete", "finished")
            - FIND_ENDPOINTS: User wants to find REST API endpoints (contains "endpoint", "API", "REST", "controller" but NOT asking about specific file)
            - FIND_COMPONENTS: User wants to find Spring components/services (contains "component", "service", "bean" but NOT asking about specific file)
            - SEARCH_CODE: User wants to search for specific code patterns (contains "search", "find", "look for" with search terms)
            - LIST_FILES: User wants to see repository file structure (contains "files", "structure", "list", "directory")
            - GET_HEALTH: User wants to check system health (contains "health", "check", "working")
            - QUERY_RESPONSE: User asks detailed questions about already analyzed code, specific files, classes, methods, or architecture (contains "explain", "what does", "how does", "describe", "detail", file paths, class names)
            - GREETING: User is greeting or asking what you can do (contains "hello", "hi", "help", "what can you do")
            
            Key distinction: 
            - Use ANALYZE_REPO only when user wants to analyze a NEW repository (with GitHub URL)
            - Use QUERY_RESPONSE when user asks about existing code, specific files, classes, or wants detailed explanations
            
            Respond with ONLY the action name (e.g., "QUERY_RESPONSE") or "UNKNOWN" if unclear.
            Do not include explanations or additional text.
            """;
    }

    private String buildIntentAnalysisPrompt(String userMessage, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User message: \"").append(userMessage).append("\"");
        
        if (context.getCurrentRepository() != null) {
            prompt.append("\nContext: Repository '").append(context.getCurrentRepository()).append("' is currently analyzed and available for queries");
            prompt.append("\nNote: User can ask detailed questions about this analyzed repository");
        } else {
            prompt.append("\nContext: No repository is currently analyzed");
            prompt.append("\nNote: User must analyze a repository first before asking about specific files or code");
        }
        
        // Additional context clues
        if (userMessage.toLowerCase().contains("explain") || userMessage.toLowerCase().contains("what does")) {
            prompt.append("\nNote: User is asking for explanations, likely wants QUERY_RESPONSE");
        }
        
        if (userMessage.contains("/") && (userMessage.contains(".java") || userMessage.contains(".kt") || userMessage.contains(".groovy"))) {
            prompt.append("\nNote: User mentions specific file path, likely wants QUERY_RESPONSE");
        }
        
        return prompt.toString();
    }

    private String extractActionFromSLMResponse(String response) {
        // Clean the response and extract action
        String cleaned = response.toUpperCase().trim();
        
        // List of valid actions
        String[] validActions = {
            "ANALYZE_REPO", "GET_STATUS", "FIND_ENDPOINTS", "FIND_COMPONENTS",
            "SEARCH_CODE", "LIST_FILES", "GET_HEALTH", "QUERY_RESPONSE", "GREETING"
        };
        
        // Check if response contains any valid action
        for (String action : validActions) {
            if (cleaned.contains(action)) {
                return action;
            }
        }
        
        return "UNKNOWN";
    }

    /**
     * NEW: Create response based on SLM intent analysis result
     */
    private LLMResponse createResponseFromIntent(String intent, String userMessage, ConversationContext context) {
        switch (intent) {
            case "ANALYZE_REPO":
                String repoUrl = extractGitHubUrl(userMessage);
                if (repoUrl != null) {
                    return createAnalyzeRepoResponse(repoUrl);
                } else {
                    // SLM detected analyze intent but no URL found
                    return createAnalysisNeededResponse(userMessage);
                }

            case "GET_STATUS":
                return createStatusResponse(userMessage, context);

            case "FIND_ENDPOINTS":
                return createFindEndpointsResponse();

            case "FIND_COMPONENTS":
                return createFindComponentsResponse();

            case "SEARCH_CODE":
                String query = extractSearchQuery(userMessage);
                return createSearchResponse(query);

            case "LIST_FILES":
                return createListFilesResponse();

            case "GET_HEALTH":
                return createHealthResponse();

            case "QUERY_RESPONSE":
                if (context.getCurrentRepository() != null) {
                    return callQueryAPI(userMessage, context);
                } else {
                    return createAnalysisNeededResponse(userMessage);
                }

            case "GREETING":
                return createGreetingResponse();

            default:
                log.warn("Unhandled SLM intent: {}", intent);
                return null;
        }
    }

    /**
     * NEW: Call the Query API for repository-specific questions
     */
    private LLMResponse callQueryAPI(String userMessage, ConversationContext context) {
        try {
            log.info("🔍 Calling Query API for: '{}'", userMessage);
            long startTime = System.currentTimeMillis();

            // Build request to your query API
            String queryApiUrl = "http://localhost:" + serverPort + "/api/query/ask";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", userMessage);
            requestBody.put("includeExplanation", true);
            requestBody.put("maxResults", 5);

            log.info("📡 Calling Query API: {}", queryApiUrl);

            // Call your existing query API
            Map<String, Object> response = restTemplate.postForObject(
                    queryApiUrl,
                    requestBody,
                    Map.class
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ Query API call completed in {} ms", duration);


            if (response != null) {
                response.forEach((key, value) ->
                        log.info("Call the Query API for repository-specific questions, inside callQueryAPI in ChatLLMService  " +
                                "Key: {}, Value: {}", key, value)
                );
            }


            if (response != null && response.get("explanation") != null) {
                String content = (String) response.get("explanation");
                log.info("✅ Query API response received: {} chars", content.length());

                // Convert to LLMResponse format
                LLMResponse llmResponse = new LLMResponse();
                llmResponse.setAction("QUERY_RESPONSE");
                llmResponse.setParameters(response); //changed from new HashMap<>() to response
                llmResponse.setResponse(content);
                llmResponse.setSuggestions(List.of(
                        "What endpoints does it have?",
                        "Show me the Spring components",
                        "Search for specific logic"
                ));
                llmResponse.setNeedsUserInput(false);

                return llmResponse;
            } else {
                log.warn("❌ Empty or invalid response from Query API");
                return callDirectLLM(userMessage, context);
            }

        } catch (Exception e) {
            log.error("❌ Error calling Query API: {}", e.getMessage());
            log.info("🔄 Falling back to direct LLM");
            return callDirectLLM(userMessage, context);
        }
    }

    /**
     * RENAMED: Direct LLM call (previously callLLM)
     */
    private LLMResponse callDirectLLM(String userMessage, ConversationContext context) {
        try {
            log.info("🤖 Calling Ollama directly with model: {}", modelName);
            long startTime = System.currentTimeMillis();

            String contextualPrompt = buildContextualPrompt(userMessage, context);
            log.info("📝 Contextual prompt: {}", contextualPrompt);

            List<OllamaMessage> messages = new ArrayList<>();
            messages.add(OllamaMessage.system(getSimpleSystemPrompt()));
            messages.add(OllamaMessage.user(contextualPrompt));

            OllamaRequest request = OllamaRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .temperature(0.1)
                    .build();

            OllamaResponse ollamaResponse = restTemplate.postForObject(
                    ollamaBaseUrl + "/api/chat",
                    request,
                    OllamaResponse.class
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ Direct LLM call completed in {} ms", duration);

            if (ollamaResponse != null && ollamaResponse.getMessage() != null) {
                String content = ollamaResponse.getMessage().getContent();
                log.info("🤖 Raw Ollama response: {}", content);
                return parseStructuredResponse(content);
            } else {
                log.error("❌ Invalid response from Ollama");
                return createFallbackResponse(userMessage);
            }

        } catch (Exception e) {
            log.error("❌ Error calling Ollama directly", e);
            return createFallbackResponse(userMessage);
        }
    }

    // NEW: Status response that checks current state
    private LLMResponse createStatusResponse(String userMessage, ConversationContext context) {
        LLMResponse response = new LLMResponse();
        response.setAction("GET_STATUS");  // New action for status checks
        response.setParameters(new HashMap<>());

        String repoUrl = extractGitHubUrl(userMessage);
        if (repoUrl != null && !repoUrl.isEmpty()) {
            // Add repository URL to parameters for status check
            Map<String, Object> params = new HashMap<>();
            params.put("repositoryUrl", repoUrl);
            response.setParameters(params);
        }

        if (context.getCurrentRepository() != null) {
            response.setResponse("Let me check the analysis status for the repository: " + context.getCurrentRepository());
        } else if (repoUrl != null && !repoUrl.isEmpty()) {
            response.setResponse("Let me check the analysis status for: " + repoUrl);
        } else {
            response.setResponse("Let me check the overall system status and any ongoing analysis.");
        }

        response.setSuggestions(List.of(
                "Show analysis progress",
                "What can I do next?",
                "Check system health"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private String getSimpleSystemPrompt() {
        return """
            You are a code analysis assistant. Respond naturally to help with code analysis tasks.
            If the user asks about repositories, endpoints, components, or code search, provide helpful guidance.
            Keep responses concise and helpful.
            """;
    }

    // Pattern-based response creators (same as before)
    private LLMResponse createGreetingResponse() {
        LLMResponse response = new LLMResponse();
        response.setAction(null);
        response.setParameters(new HashMap<>());
        response.setResponse("Hi! I'm a code analysis assistant. I can help you analyze GitHub repositories, find REST endpoints, discover Spring components, and search through code. What would you like me to help you with?");
        response.setSuggestions(List.of(
                "Analyze a GitHub repository",
                "Check system health",
                "What endpoints does it have?"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createAnalyzeRepoResponse(String repoUrl) {
        LLMResponse response = new LLMResponse();
        response.setAction("ANALYZE_REPO");

        Map<String, Object> params = new HashMap<>();
        params.put("repositoryUrl", repoUrl);
        response.setParameters(params);

        response.setResponse("Perfect! I'll analyze the repository at " + repoUrl + " for you. This will start the cloning and analysis process, which may take a few minutes depending on the repository size.");
        response.setSuggestions(List.of(
                "Check analysis status",
                "What REST endpoints does it have?",
                "Show me the Spring components"
        ));
        response.setNeedsUserInput(false);

        return response;
    }

    private LLMResponse createFindEndpointsResponse() {
        LLMResponse response = new LLMResponse();
        response.setAction("FIND_ENDPOINTS");
        response.setParameters(new HashMap<>());
        response.setResponse("I'll search for all REST endpoints in the analyzed repository. This includes @RestController, @RequestMapping, and other Spring web annotations.");
        response.setSuggestions(List.of(
                "Show me the Spring components",
                "Search for authentication logic",
                "Check analysis status"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createFindComponentsResponse() {
        LLMResponse response = new LLMResponse();
        response.setAction("FIND_COMPONENTS");
        response.setParameters(new HashMap<>());
        response.setResponse("I'll find all Spring components in the repository, including @Service, @Component, @Repository, and @Controller classes.");
        response.setSuggestions(List.of(
                "What endpoints does it have?",
                "Search for specific business logic",
                "Check system health"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createHealthResponse() {
        LLMResponse response = new LLMResponse();
        response.setAction("GET_HEALTH");
        response.setParameters(new HashMap<>());
        response.setResponse("Let me check the system health and verify that all services (code analysis, vector store, and LLM) are running properly.");
        response.setSuggestions(List.of(
                "Analyze a repository",
                "What can you do for me?"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createListFilesResponse() {
        LLMResponse response = new LLMResponse();
        response.setAction("LIST_FILES");
        response.setParameters(new HashMap<>());
        response.setResponse("I'll show you the file structure of the currently analyzed repository, focusing on Java source files.");
        response.setSuggestions(List.of(
                "Find REST endpoints",
                "Show Spring components",
                "Search for specific code"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createSearchResponse(String query) {
        LLMResponse response = new LLMResponse();
        response.setAction("SEARCH_CODE");

        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        response.setParameters(params);

        response.setResponse("I'll search the analyzed codebase for: '" + query + "'. This will look through classes, methods, and comments for relevant matches.");
        response.setSuggestions(List.of(
                "Find endpoints",
                "Show components",
                "Try another search"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createGenericHelpResponse(String userMessage) {
        LLMResponse response = new LLMResponse();
        response.setAction(null);
        response.setParameters(new HashMap<>());
        response.setResponse("I can help you analyze code repositories! Here's what I can do:\n\n" +
                "• **Analyze repositories**: 'Analyze https://github.com/owner/repo'\n" +
                "• **Check status**: 'What is the analysis status?'\n" +
                "• **Find endpoints**: 'What endpoints does it have?'\n" +
                "• **Discover components**: 'Show me the Spring components'\n" +
                "• **Search code**: 'Search for authentication'\n" +
                "• **Check health**: 'Check system health'\n\n" +
                "What would you like me to help you with?");
        response.setSuggestions(List.of(
                "Analyze a GitHub repository",
                "Check analysis status",
                "What can you do?"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    private LLMResponse createFallbackResponse(String userMessage) {
        LLMResponse response = new LLMResponse();
        response.setAction(null);
        response.setParameters(new HashMap<>());
        response.setResponse("I understand you're asking about: '" + userMessage + "'. I can help you analyze code repositories. Would you like to analyze a GitHub repository or ask about a specific aspect of code analysis?");
        response.setSuggestions(List.of(
                "Analyze a GitHub repository",
                "Check analysis status",
                "Check system health"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    /**
     * Check if the question is about code/repository but no repository is analyzed
     */
    private boolean isRepositorySpecificQuestion(String message) {
        String lowerMessage = message.toLowerCase();
        return containsAny(lowerMessage,
                "controller", "service", "class", "method", "function",
                "code", "implementation", "logic", "endpoint", "api",
                "spring", "mvc", "rest", "repository", "component");
    }

    /**
     * Suggest analyzing a repository first for repository-specific questions
     */
    private LLMResponse createAnalysisNeededResponse(String userMessage) {
        LLMResponse response = new LLMResponse();
        response.setAction(null);
        response.setParameters(new HashMap<>());
        response.setResponse("I'd be happy to help you understand code like ProductController! However, I need to analyze a repository first to give you specific information about it.\n\n" +
                "Please provide a GitHub repository URL to analyze, like:\n" +
                "**'Analyze https://github.com/owner/repository'**\n\n" +
                "Once I've analyzed the repository, I can answer detailed questions about:\n" +
                "• What specific controllers do\n" +
                "• How services work\n" +
                "• REST endpoints available\n" +
                "• Spring components and their relationships");
        response.setSuggestions(List.of(
                "Analyze https://github.com/springframeworkguru/springbootwebapp",
                "What can you analyze?",
                "Check system health"
        ));
        response.setNeedsUserInput(false);
        return response;
    }

    // Helper methods
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                log.debug("Found keyword '{}' in text", keyword);
                return true;
            }
        }
        return false;
    }

    private String extractGitHubUrl(String text) {
        java.util.regex.Matcher matcher = GITHUB_URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractSearchQuery(String text) {
        String lowerText = text.toLowerCase();
        String[] patterns = {"search for ", "find ", "look for ", "search "};
        for (String pattern : patterns) {
            int index = lowerText.indexOf(pattern);
            if (index != -1) {
                return text.substring(index + pattern.length()).trim();
            }
        }
        return text.trim(); // Fallback to entire message
    }

    private String buildContextualPrompt(String userMessage, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User: ").append(userMessage);

        if (context.getCurrentRepository() != null) {
            prompt.append("\nCurrent repository: ").append(context.getCurrentRepository());
        }

        return prompt.toString();
    }

    private LLMResponse parseStructuredResponse(String response) {
        // For now, just create a simple response wrapper
        LLMResponse llmResponse = new LLMResponse();
        llmResponse.setAction("DIRECT_LLM_RESPONSE");
        llmResponse.setParameters(new HashMap<>());
        llmResponse.setResponse(response);
        llmResponse.setSuggestions(List.of(
                "What else can you tell me?",
                "Find REST endpoints",
                "Check system status"
        ));
        llmResponse.setNeedsUserInput(false);
        return llmResponse;
    }
}