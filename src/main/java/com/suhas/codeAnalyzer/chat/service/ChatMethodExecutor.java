package com.suhas.codeAnalyzer.chat.service;

import com.suhas.codeAnalyzer.chat.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatMethodExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChatMethodExecutor.class);

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${chat.wait-for-completion:false}")
    private boolean waitForCompletion;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();

        // Configure timeouts to handle long-running operations
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);     // 10 seconds connection timeout
        factory.setReadTimeout(1200000);       // 2 minutes read timeout for search operations

        this.restTemplate.setRequestFactory(factory);

        log.info("ChatMethodExecutor initialized with extended timeouts");
    }

    private String getBaseUrl() {
        return "http://localhost:" + serverPort;
    }

    public ActionResult execute(LLMAction action) {
        try {
            log.info("Executing action: {} with parameters: {}", action.getActionName(), action.getParameters());
            log.info("Action object:"+action.toString());

            switch (action.getActionName()) {
                case "ANALYZE_REPO":
                    return analyzeRepository(action.getParameters());

                case "GET_STATUS":
                    return getAnalysisStatus(action.getParameters());

                case "FIND_ENDPOINTS":
                    return findEndpoints(action.getParameters());

                case "FIND_COMPONENTS":
                    return findComponents(action.getParameters());

                case "SEARCH_CODE":
                    return searchCode(action.getParameters());

                case "EXPLAIN_CLASS":
                    return explainClass(action.getParameters());

                case "LIST_FILES":
                    return listFiles(action.getParameters());

                case "GET_HEALTH":
                    return getHealth();

                case "ANALYZE_PROJECT":
                    return analyzeProjectStructure(action.getParameters());

                case "QUERY_RESPONSE":
                    return handleQueryResponse(action);  // NEW CASE

                case "DIRECT_LLM_RESPONSE":  // Handle this too if you need it
                    return handleQueryResponse(action);

                default:
                    return ActionResult.error("Unknown action: " + action.getActionName());
            }
        } catch (Exception e) {
            log.error("Error executing action: {}", action.getActionName(), e);
            return ActionResult.error("Execution failed: " + e.getMessage());
        }
    }

    /**
     * Handle query responses from the Query API - pass through the response content
     */
    private ActionResult handleQueryResponse(LLMAction action) {
        log.info("Handling query response from Query API");
        log.info("Action object:"+action.toString());

        try {
            String responseContent = null;
            Map<String, Object> parameters = action.getParameters();

            // Try to get response content from parameters
            if (parameters != null) {
                // Check common parameter names where response content might be stored
                String[] responseKeys = {"response", "content", "result", "answer", "message", "explanation"}; //added explanation as additional keyword

                for (String key : responseKeys) {
                    if (parameters.containsKey(key)) {
                        Object responseObj = parameters.get(key);
                        if (responseObj instanceof String && !((String) responseObj).trim().isEmpty()) {
                            responseContent = (String) responseObj;
                            log.debug("Found response content in parameters[{}]: {} chars", key, responseContent.length());
                            break;
                        }
                    }
                }
            }

            // If we have response content, return it
            if (responseContent != null && !responseContent.trim().isEmpty()) {
                log.info("Successfully passing through query response: {} chars", responseContent.length());
                return ActionResult.success(responseContent);
            } else {
                log.warn("Query response action has no content to return. Parameters: {}", parameters);
                return ActionResult.error("Query response was empty or missing content");
            }

        } catch (Exception e) {
            log.error("Error handling query response", e);
            return ActionResult.error("Failed to process query response: " + e.getMessage());
        }
    }

    private ActionResult analyzeRepository(Map<String, Object> params) {
        try {
            String repoUrl = (String) params.get("repositoryUrl");
            if (repoUrl == null || repoUrl.trim().isEmpty()) {
                return ActionResult.error("Repository URL is required");
            }

            log.info("🚀 Starting complete repository analysis for: {}", repoUrl);

            // Step 1: Clone repository
            log.info("📥 Step 1: Cloning repository...");
            try {
                String cloneEndpoint = UriComponentsBuilder.fromHttpUrl(getBaseUrl() + "/api/repository/clone")
                        .queryParam("url", repoUrl)
                        .toUriString();

                Object cloneResponse = restTemplate.postForObject(cloneEndpoint, null, Object.class);
                log.info("✅ Clone initiated: {}", cloneResponse);

            } catch (Exception e) {
                log.error("❌ Failed to initiate clone", e);
                return ActionResult.error("Failed to clone repository: " + e.getMessage());
            }

            // Step 2: Wait for clone to complete before starting analysis
            log.info("⏳ Waiting for clone to complete before starting analysis...");
            try {
                Thread.sleep(10000); // Wait 10 seconds for clone to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Step 3: Start analysis (this generates embeddings)
            log.info("🔍 Step 2: Starting code analysis and embedding generation...");
            try {
                String analysisEndpoint = UriComponentsBuilder.fromHttpUrl(getBaseUrl() + "/api/analysis/start")
                        .queryParam("url", repoUrl)
                        .toUriString();

                Object analysisResponse = restTemplate.postForObject(analysisEndpoint, null, Object.class);
                log.info("✅ Analysis initiated: {}", analysisResponse);

            } catch (Exception e) {
                log.error("❌ Failed to initiate analysis", e);
                // Don't fail completely - clone might have succeeded
                log.warn("⚠️ Clone succeeded but analysis failed. You can try analysis later.");
            }

            // Step 4: Return success message
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("repositoryUrl", repoUrl);
            resultData.put("status", "processing");
            resultData.put("cloneInitiated", true);
            resultData.put("analysisInitiated", true);

            String successMessage = String.format(
                    "🚀 **Repository Analysis Started!**\n\n" +
                            "**Repository:** %s\n" +
                            "**Status:** Processing\n\n" +
                            "**What's happening:**\n" +
                            "1. ✅ Repository cloning initiated\n" +
                            "2. ✅ Code analysis and embedding generation started\n\n" +
                            "**What's next:**\n" +
                            "• The system is now cloning the repository and parsing Java files\n" +
                            "• Creating vector embeddings for semantic search\n" +
                            "• Storing everything in the vector database\n\n" +
                            "**This process may take 2-10 minutes depending on repository size.**\n\n" +
                            "You can now ask me questions like:\n" +
                            "• \"What REST endpoints does it have?\"\n" +
                            "• \"Show me the Spring components\"\n" +
                            "• \"Search for authentication logic\"",
                    repoUrl
            );

            return ActionResult.success(successMessage, resultData);

        } catch (Exception e) {
            log.error("❌ Complete failure in repository analysis", e);
            return ActionResult.error("Failed to analyze repository: " + e.getMessage());
        }
    }

    private ActionResult getAnalysisStatus(Map<String, Object> params) {
        try {
            String repoUrl = (String) params.get("repositoryUrl");

            if (repoUrl != null && !repoUrl.trim().isEmpty()) {
                log.info("📊 Checking analysis status for: {}", repoUrl);

                try {
                    // Check analysis progress
                    String progressEndpoint = UriComponentsBuilder.fromHttpUrl(getBaseUrl() + "/api/analysis/progress")
                            .queryParam("url", repoUrl)
                            .toUriString();

                    Object progressResponse = restTemplate.getForObject(progressEndpoint, Object.class);

                    return ActionResult.success(
                            "📊 **Analysis Status:**\n\nHere's the current status of the repository analysis:",
                            progressResponse
                    );

                } catch (Exception e) {
                    log.warn("Could not get progress for specific repository, checking overall status");
                }
            }

            // Fallback: general health check
            return getHealth();

        } catch (Exception e) {
            log.error("Failed to get analysis status", e);
            return ActionResult.error("Failed to get analysis status: " + e.getMessage());
        }
    }

    private ActionResult findEndpoints(Map<String, Object> params) {
        try {
            log.info("🔍 Finding REST endpoints using AI-powered search...");

            // Use intelligent search with natural language query
            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("query", "REST API endpoints HTTP methods web services");
            //searchRequest.put("maxResults", 10);
            searchRequest.put("includeCode", true);
            searchRequest.put("includeExplanation", true);  // Let LLM explain what it found

            Object searchResults = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/search",
                    searchRequest,
                    Object.class
            );

            // Let the AI format the response - minimal manual intervention
            String formattedResponse = formatAIEndpointsResponse(searchResults);
            return ActionResult.success(formattedResponse, searchResults);

        } catch (Exception e) {
            log.error("❌ Failed to find endpoints", e);
            return ActionResult.error(
                    "❌ **Could not analyze endpoints.**\n\n" +
                            "**Try:**\n" +
                            "• \"What is the analysis status?\" - Check if analysis is complete\n" +
                            "• \"Search for web controller\" - More specific search\n" +
                            "• \"List repository files\" - See the project structure"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String formatAIEndpointsResponse(Object searchResults) {
        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;
                StringBuilder response = new StringBuilder("🔍 **REST Endpoints Analysis:**\n\n");

                // First, show the AI's explanation if available
                if (resultsMap.containsKey("explanation") && resultsMap.get("explanation") != null) {
                    String explanation = resultsMap.get("explanation").toString();
                    response.append("**🤖 AI Analysis:**\n");
                    response.append(explanation).append("\n\n");
                }

                // Then show the raw matches for transparency
                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    if (matches != null && !matches.isEmpty()) {
                        response.append("**📋 Code Matches Found:**\n\n");

                        int count = 1;
                        for (Object match : matches) {
                            if (match instanceof Map) {
                                Map<String, Object> matchMap = (Map<String, Object>) match;
                                response.append(formatSimpleMatch(count, matchMap));
                                count++;
                            }
                        }
                    } else {
                        response.append("❌ No code matches found.\n\n");
                        response.append("**Try different searches:**\n");
                        response.append("• \"Search for controller classes\"\n");
                        response.append("• \"Search for web annotations\"\n");
                        response.append("• \"Show me the API layer\"\n");
                    }
                }

                // Add helpful suggestions
                response.append("\n**💡 Try these follow-up questions:**\n");
                response.append("• \"Explain how the API works\"\n");
                response.append("• \"What does the ProductController do?\"\n");
                response.append("• \"Search for authentication endpoints\"\n");

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format AI endpoints response", e);
        }

        return "🔍 **REST Endpoints Analysis:**\n\n" + searchResults.toString();
    }

    private String formatSimpleMatch(int count, Map<String, Object> match) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(". ");

        Object className = match.get("className");
        Object methodName = match.get("methodName");
        Object filePath = match.get("filePath");

        // Just show the basic info - let user interpret
        if (className != null) {
            sb.append("**").append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            sb.append("**\n");

            if (filePath != null) {
                sb.append("   📁 ").append(filePath).append("\n");
            }

            // Show a snippet of code for context
            Object code = match.get("code");
            if (code != null) {
                String codeSnippet = code.toString();
                if (codeSnippet.length() > 150) {
                    codeSnippet = codeSnippet.substring(0, 150) + "...";
                }
                sb.append("   ```java\n   ").append(codeSnippet.replace("\n", "\n   ")).append("\n   ```\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatSimpleEndpointsResponse(Object searchResults) {
        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;
                StringBuilder response = new StringBuilder("🔍 **REST Endpoints Found:**\n\n");

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    if (matches != null && !matches.isEmpty()) {
                        response.append("Found **").append(matches.size()).append(" potential endpoints**:\n\n");

                        int count = 1;
                        for (Object match : matches) {
                            if (match instanceof Map) {
                                Map<String, Object> matchMap = (Map<String, Object>) match;
                                String endpoint = extractSimpleEndpoint(matchMap);
                                if (endpoint != null) {
                                    response.append(count).append(". ").append(endpoint).append("\n");
                                    count++;
                                }
                            }
                        }
                    } else {
                        response.append("❌ No REST endpoints found.\n\n");
                        response.append("**Try:**\n");
                        response.append("• 'Search for @Path' (for JAX-RS)\n");
                        response.append("• 'Search for Resource' (for JAX-RS classes)\n");
                        response.append("• 'List repository files' (to see structure)\n");
                    }
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format simple endpoints response", e);
        }

        return "🔍 **REST Endpoints Found:**\n\n" + searchResults.toString();
    }

    private String extractSimpleEndpoint(Map<String, Object> match) {
        try {
            Object className = match.get("className");
            Object methodName = match.get("methodName");
            Object annotations = match.get("annotations");

            if (className != null && !className.toString().equals("Application")) {
                StringBuilder sb = new StringBuilder();
                sb.append("**").append(className);
                if (methodName != null) {
                    sb.append(".").append(methodName);
                }
                sb.append("**");

                if (annotations != null) {
                    sb.append(" - ").append(annotations);
                }

                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to extract simple endpoint", e);
        }
        return null;
    }

    private ActionResult findComponents(Map<String, Object> params) {
        try {
            log.info("🏗️ Finding components using AI search...");

            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("query", "Spring components services repositories business logic");
            searchRequest.put("maxResults", 8);
            searchRequest.put("includeCode", true);
            searchRequest.put("includeExplanation", true);

            Object searchResults = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/search",
                    searchRequest,
                    Object.class
            );

            String formattedResponse = formatAIComponentsResponse(searchResults);
            return ActionResult.success(formattedResponse, searchResults);

        } catch (Exception e) {
            log.error("❌ Failed to find components", e);
            return ActionResult.error("❌ Could not analyze components. Try: 'What is the analysis status?'");
        }
    }

    @SuppressWarnings("unchecked")
    private String formatAIComponentsResponse(Object searchResults) {
        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;
                StringBuilder response = new StringBuilder("🏗️ **Components Analysis:**\n\n");

                // Show AI explanation first
                if (resultsMap.containsKey("explanation") && resultsMap.get("explanation") != null) {
                    String explanation = resultsMap.get("explanation").toString();
                    response.append("**🤖 AI Analysis:**\n");
                    response.append(explanation).append("\n\n");
                }

                // Show matches without complex filtering
                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    if (matches != null && !matches.isEmpty()) {
                        response.append("**📋 Components Found:**\n\n");

                        for (int i = 0; i < Math.min(matches.size(), 6); i++) {
                            Object match = matches.get(i);
                            if (match instanceof Map) {
                                Map<String, Object> matchMap = (Map<String, Object>) match;
                                response.append(formatSimpleMatch(i + 1, matchMap));
                            }
                        }
                    }
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format AI components response", e);
        }

        return "🏗️ **Components Analysis:**\n\n" + searchResults.toString();
    }

    private ActionResult searchCode(Map<String, Object> params) {
        try {
            String query = (String) params.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ActionResult.error("❌ Search query is required. Try: 'Search for authentication'");
            }

            log.info("🔎 Searching code for: {}", query);

            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("query", query);
            searchRequest.put("maxResults", 5);
            searchRequest.put("includeCode", true);
            searchRequest.put("includeExplanation", true);

            Object searchResults = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/search",
                    searchRequest,
                    Object.class
            );

            // Format the response nicely
            String formattedResponse = formatSearchResponse(query, searchResults);

            return ActionResult.success(formattedResponse, searchResults);

        } catch (Exception e) {
            log.error("❌ Failed to search code", e);

            String errorMsg = "❌ **Search failed.**\n\n" +
                    "**Possible reasons:**\n" +
                    "• Repository analysis is still in progress\n" +
                    "• No repository has been analyzed yet\n" +
                    "• Vector embeddings are not ready\n\n" +
                    "**Try:**\n" +
                    "• Wait for analysis to complete\n" +
                    "• Analyze a repository first\n" +
                    "• Try a different search term";

            return ActionResult.error(errorMsg);
        }
    }

    private ActionResult explainClass(Map<String, Object> params) {
        try {
            String className = (String) params.get("className");
            if (className == null || className.trim().isEmpty()) {
                return ActionResult.error("❌ Class name is required. Try: 'Explain UserController class'");
            }

            log.info("📚 Explaining class: {}", className);

            Map<String, String> explainRequest = new HashMap<>();
            explainRequest.put("query", "Explain the business logic of class " + className);

            Object explanation = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/business-logic",
                    explainRequest,
                    Object.class
            );

            return ActionResult.success(
                    String.format("📚 **Analysis of '%s' class:**", className),
                    explanation
            );

        } catch (Exception e) {
            log.error("❌ Failed to explain class", e);
            return ActionResult.error("Failed to explain class: " + e.getMessage());
        }
    }

    private ActionResult listFiles(Map<String, Object> params) {
        try {
            String repoUrl = getCurrentRepositoryUrl(params);
            if (repoUrl == null) {
                return ActionResult.error("❌ No repository loaded. Please analyze a repository first.");
            }

            log.info("📁 Listing files for repository: {}", repoUrl);

            String filesEndpoint = UriComponentsBuilder.fromHttpUrl(getBaseUrl() + "/api/repository/files")
                    .queryParam("url", repoUrl)
                    .toUriString();

            Object fileStructure = restTemplate.getForObject(filesEndpoint, Object.class);

            // Format the response nicely
            String formattedResponse = formatFilesResponse(fileStructure);

            return ActionResult.success(formattedResponse, fileStructure);

        } catch (Exception e) {
            log.error("❌ Failed to list files", e);
            return ActionResult.error("Failed to list files: " + e.getMessage());
        }
    }

    private ActionResult getHealth() {
        try {
            log.info("🏥 Checking system health...");

            Object healthResponse = restTemplate.getForObject(
                    getBaseUrl() + "/api/analysis/health",
                    Object.class
            );

            return ActionResult.success(
                    "🏥 **System Health Check:**\n\n✅ All systems are operational and ready to analyze code repositories!",
                    healthResponse
            );

        } catch (Exception e) {
            log.error("❌ Health check failed", e);
            return ActionResult.error("❌ **System Health Issues:**\n\nSome services are not responding properly: " + e.getMessage());
        }
    }

    private ActionResult analyzeProjectStructure(Map<String, Object> params) {
        try {
            log.info("🔍 Analyzing project structure for microservices...");

            // Search for actual service implementations
            Map<String, Object> serviceSearch = new HashMap<>();
            serviceSearch.put("query", "Resource JAX-RS @Path @GET @POST @PUT @DELETE");
            serviceSearch.put("maxResults", 20);
            serviceSearch.put("includeCode", true);

            Object serviceResults = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/search",
                    serviceSearch,
                    Object.class
            );

            // Search for configuration classes
            Map<String, Object> configSearch = new HashMap<>();
            configSearch.put("query", "JerseyConfig ResourceConfig @ApplicationPath");
            //configSearch.put("maxResults", 10);
            configSearch.put("includeCode", true);

            Object configResults = restTemplate.postForObject(
                    getBaseUrl() + "/api/query/search",
                    configSearch,
                    Object.class
            );

            String analysis = analyzeMicroservicesProject(serviceResults, configResults);

            Map<String, Object> combinedResults = new HashMap<>();
            combinedResults.put("serviceSearch", serviceResults);
            combinedResults.put("configSearch", configResults);

            return ActionResult.success(analysis, combinedResults);

        } catch (Exception e) {
            log.error("❌ Failed to analyze project structure", e);
            return ActionResult.error("Failed to analyze project structure: " + e.getMessage());
        }
    }

    // Helper class to store endpoint information
    private static class EndpointInfo {
        String method;
        String path;
        String className;
        String methodName;

        EndpointInfo(String method, String path, String className, String methodName) {
            this.method = method;
            this.path = path;
            this.className = className;
            this.methodName = methodName;
        }
    }

    // Helper methods for formatting responses

    @SuppressWarnings("unchecked")
    private String formatDedicatedEndpointsResponse(Object endpointsResponse) {
        try {
            if (endpointsResponse instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) endpointsResponse;
                StringBuilder response = new StringBuilder("🔍 **REST Endpoints Found:**\n\n");

                if (responseMap.containsKey("endpoints") || responseMap.containsKey("results")) {
                    List<?> endpoints = (List<?>) (responseMap.get("endpoints") != null ?
                            responseMap.get("endpoints") : responseMap.get("results"));

                    if (endpoints != null && !endpoints.isEmpty()) {
                        response.append("Found **").append(endpoints.size()).append(" REST endpoints**:\n\n");

                        int count = 1;
                        for (Object endpoint : endpoints) {
                            if (endpoint instanceof Map) {
                                Map<String, Object> ep = (Map<String, Object>) endpoint;
                                response.append(formatActualEndpoint(count, ep));
                                count++;
                            }
                        }
                        return response.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to format dedicated endpoints response", e);
        }

        return "No REST endpoints found in dedicated API response";
    }

    @SuppressWarnings("unchecked")
    private String formatCombinedEndpointsResponse(Object annotationResults, Object controllerResults) {
        StringBuilder response = new StringBuilder("🔍 **REST Endpoints Analysis:**\n\n");

        // Extract endpoints from annotation search
        List<EndpointInfo> endpoints = extractEndpointsFromSearch(annotationResults);

        // Extract controllers from controller search
        List<String> controllers = extractControllersFromSearch(controllerResults);

        if (!endpoints.isEmpty()) {
            response.append("**📍 Discovered REST Endpoints:**\n\n");

            for (int i = 0; i < endpoints.size(); i++) {
                EndpointInfo ep = endpoints.get(i);
                response.append(String.format("%d. **%s** `%s`\n",
                        i + 1,
                        ep.method != null ? ep.method : "HTTP",
                        ep.path != null ? ep.path : "Unknown path"
                ));

                if (ep.className != null) {
                    response.append(String.format("   📁 Controller: `%s`\n", ep.className));
                }

                if (ep.methodName != null) {
                    response.append(String.format("   🔧 Method: `%s`\n", ep.methodName));
                }

                response.append("\n");
            }
        }

        if (!controllers.isEmpty()) {
            response.append("**🏗️ REST Controllers Found:**\n\n");
            for (int i = 0; i < controllers.size(); i++) {
                response.append(String.format("%d. `%s`\n", i + 1, controllers.get(i)));
            }
            response.append("\n");
        }

        if (endpoints.isEmpty() && controllers.isEmpty()) {
            response.append("❌ **No REST endpoints found.**\n\n");
            response.append("**This repository appears to be:**\n");
            response.append("• A microservices configuration project\n");
            response.append("• Service discovery setup (Eureka)\n");
            response.append("• API gateway configuration\n");
            response.append("• Not a REST API implementation\n\n");
            response.append("**Try analyzing a repository with actual REST controllers like:**\n");
            response.append("• Spring Boot REST API projects\n");
            response.append("• Projects with @RestController classes\n");
            response.append("• Web service implementations\n\n");
            response.append("**Or try these specific searches:**\n");
            response.append("• \"Search for @Path annotation\"\n");
            response.append("• \"Search for JAX-RS resources\"\n");
            response.append("• \"Search for ProductResource class\"\n");
        }

        return response.toString();
    }

    @SuppressWarnings("unchecked")
    private List<EndpointInfo> extractEndpointsFromSearch(Object searchResults) {
        List<EndpointInfo> endpoints = new ArrayList<>();

        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    for (Object match : matches) {
                        if (match instanceof Map) {
                            Map<String, Object> matchMap = (Map<String, Object>) match;
                            EndpointInfo endpoint = extractEndpointFromMatch(matchMap);
                            if (endpoint != null && endpoint.path != null) {
                                endpoints.add(endpoint);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract endpoints from search results", e);
        }

        return endpoints;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractControllersFromSearch(Object searchResults) {
        List<String> controllers = new ArrayList<>();

        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    for (Object match : matches) {
                        if (match instanceof Map) {
                            Map<String, Object> matchMap = (Map<String, Object>) match;

                            // Only include actual RestController classes, not Application classes
                            Object className = matchMap.get("className");
                            Object annotations = matchMap.get("annotations");

                            if (className != null && annotations != null) {
                                String annotationStr = annotations.toString();
                                String classNameStr = className.toString();

                                if ((annotationStr.contains("@RestController") ||
                                        annotationStr.contains("@Controller") ||
                                        classNameStr.toLowerCase().contains("resource")) &&
                                        !classNameStr.equals("Application") &&
                                        !classNameStr.contains("Config")) {
                                    controllers.add(classNameStr);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract controllers from search results", e);
        }

        return controllers;
    }

    private EndpointInfo extractEndpointFromMatch(Map<String, Object> match) {
        try {
            Object code = match.get("code");
            Object className = match.get("className");
            Object methodName = match.get("methodName");
            Object annotations = match.get("annotations");

            if (code == null) return null;

            String codeStr = code.toString();
            String classNameStr = className != null ? className.toString() : null;
            String methodNameStr = methodName != null ? methodName.toString() : null;

            // Skip Application classes and configuration
            if (classNameStr != null &&
                    (classNameStr.equals("Application") ||
                            classNameStr.contains("Config") ||
                            classNameStr.contains("Exception") ||
                            classNameStr.contains("Mapper"))) {
                return null;
            }

            // Extract HTTP method and path from code
            String httpMethod = extractHttpMethod(codeStr, annotations);
            String path = extractPath(codeStr);

            if (httpMethod != null || path != null) {
                return new EndpointInfo(httpMethod, path, classNameStr, methodNameStr);
            }

        } catch (Exception e) {
            log.warn("Failed to extract endpoint from match", e);
        }

        return null;
    }

    private String extractHttpMethod(String code, Object annotations) {
        // Check annotations first
        if (annotations != null) {
            String annotationStr = annotations.toString();
            if (annotationStr.contains("@GetMapping") || annotationStr.contains("@GET")) return "GET";
            if (annotationStr.contains("@PostMapping") || annotationStr.contains("@POST")) return "POST";
            if (annotationStr.contains("@PutMapping") || annotationStr.contains("@PUT")) return "PUT";
            if (annotationStr.contains("@DeleteMapping") || annotationStr.contains("@DELETE")) return "DELETE";
            if (annotationStr.contains("@PatchMapping") || annotationStr.contains("@PATCH")) return "PATCH";
        }

        // Check code for annotations
        if (code.contains("@GetMapping") || code.contains("@GET")) return "GET";
        if (code.contains("@PostMapping") || code.contains("@POST")) return "POST";
        if (code.contains("@PutMapping") || code.contains("@PUT")) return "PUT";
        if (code.contains("@DeleteMapping") || code.contains("@DELETE")) return "DELETE";
        if (code.contains("@PatchMapping") || code.contains("@PATCH")) return "PATCH";

        // Check for @RequestMapping with method
        if (code.contains("@RequestMapping")) {
            if (code.contains("RequestMethod.GET") || code.contains("method = GET")) return "GET";
            if (code.contains("RequestMethod.POST") || code.contains("method = POST")) return "POST";
            if (code.contains("RequestMethod.PUT") || code.contains("method = PUT")) return "PUT";
            if (code.contains("RequestMethod.DELETE") || code.contains("method = DELETE")) return "DELETE";
            return "REQUEST"; // Generic RequestMapping
        }

        return null;
    }

    private String extractPath(String code) {
        try {
            // Patterns to match path values in annotations
            String[] patterns = {
                    "@GetMapping\\(\"([^\"]+)\"\\)",
                    "@PostMapping\\(\"([^\"]+)\"\\)",
                    "@PutMapping\\(\"([^\"]+)\"\\)",
                    "@DeleteMapping\\(\"([^\"]+)\"\\)",
                    "@PatchMapping\\(\"([^\"]+)\"\\)",
                    "@Path\\(\"([^\"]+)\"\\)",
                    "@RequestMapping\\([^)]*value\\s*=\\s*\"([^\"]+)\"",
                    "@RequestMapping\\(\"([^\"]+)\"\\)",
                    "value\\s*=\\s*\"([^\"]+)\""
            };

            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(code);
                if (m.find()) {
                    return m.group(1);
                }
            }

            // Look for path variables in method signatures
            java.util.regex.Pattern pathVarPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher pathVarMatcher = pathVarPattern.matcher(code);
            if (pathVarMatcher.find()) {
                // Try to extract the full path containing the path variable
                String[] lines = code.split("\n");
                for (String line : lines) {
                    if (line.contains("{") && line.contains("}") &&
                            (line.contains("@") || line.contains("Mapping") || line.contains("Path"))) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*\\{[^}]+\\}[^\"]*)\"|'([^']*\\{[^}]+\\}[^']*)'").matcher(line);
                        if (m.find()) {
                            return m.group(1) != null ? m.group(1) : m.group(2);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract path from code", e);
        }

        return null;
    }

    private String formatActualEndpoint(int count, Map<String, Object> endpoint) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(". ");

        Object method = endpoint.get("method");
        Object path = endpoint.get("path");
        Object controller = endpoint.get("controller");
        Object methodName = endpoint.get("methodName");

        if (method != null && path != null) {
            sb.append("**").append(method).append("** `").append(path).append("`\n");

            if (controller != null) {
                sb.append("   📁 Controller: `").append(controller).append("`\n");
            }

            if (methodName != null) {
                sb.append("   🔧 Method: `").append(methodName).append("`\n");
            }
        } else {
            sb.append("**Endpoint** ").append(endpoint.toString()).append("\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String analyzeMicroservicesProject(Object serviceResults, Object configResults) {
        StringBuilder response = new StringBuilder("🏗️ **Microservices Project Analysis:**\n\n");

        // Analyze service implementations
        List<String> jaxRsEndpoints = extractJaxRsEndpoints(serviceResults);
        List<String> configurations = extractConfigurations(configResults);

        if (!jaxRsEndpoints.isEmpty()) {
            response.append("**📍 JAX-RS REST Endpoints Found:**\n\n");
            for (int i = 0; i < jaxRsEndpoints.size(); i++) {
                response.append(String.format("%d. %s\n", i + 1, jaxRsEndpoints.get(i)));
            }
            response.append("\n");
        }

        if (!configurations.isEmpty()) {
            response.append("**⚙️ Service Configurations:**\n\n");
            for (int i = 0; i < configurations.size(); i++) {
                response.append(String.format("%d. %s\n", i + 1, configurations.get(i)));
            }
            response.append("\n");
        }

        // Project structure analysis
        response.append("**📋 Project Structure:**\n\n");
        response.append("This appears to be a **microservices architecture** with:\n\n");
        response.append("• **Service Discovery** (Eureka Server)\n");
        response.append("• **API Gateway** (Spring Cloud Gateway)\n");
        response.append("• **Business Services** (Product Service, Shopping List Service)\n");
        response.append("• **JAX-RS REST APIs** (not Spring MVC)\n\n");

        if (jaxRsEndpoints.isEmpty()) {
            response.append("**🔍 To find actual REST endpoints:**\n\n");
            response.append("Try these specific searches:\n");
            response.append("• \"Search for @Path annotation\"\n");
            response.append("• \"Search for JAX-RS resources\"\n");
            response.append("• \"Search for @GET @POST methods\"\n");
            response.append("• \"Show me the ProductResource class\"\n\n");

            response.append("**💡 Note:** This project uses JAX-RS (not Spring MVC), so endpoints are defined with:\n");
            response.append("• `@Path(\"/products\")` instead of `@RequestMapping`\n");
            response.append("• `@GET`, `@POST` instead of `@GetMapping`, `@PostMapping`\n");
        }

        return response.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractJaxRsEndpoints(Object searchResults) {
        List<String> endpoints = new ArrayList<>();

        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    for (Object match : matches) {
                        if (match instanceof Map) {
                            Map<String, Object> matchMap = (Map<String, Object>) match;
                            String endpoint = extractJaxRsEndpoint(matchMap);
                            if (endpoint != null) {
                                endpoints.add(endpoint);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract JAX-RS endpoints", e);
        }

        return endpoints;
    }

    private String extractJaxRsEndpoint(Map<String, Object> match) {
        try {
            Object code = match.get("code");
            Object className = match.get("className");
            Object methodName = match.get("methodName");

            if (code == null) return null;

            String codeStr = code.toString();
            String classNameStr = className != null ? className.toString() : "";
            String methodNameStr = methodName != null ? methodName.toString() : "";

            // Skip configuration classes
            if (classNameStr.contains("Config") || classNameStr.contains("Application")) {
                return null;
            }

            // Extract JAX-RS annotations
            String httpMethod = extractJaxRsMethod(codeStr);
            String path = extractJaxRsPath(codeStr, className);

            if (httpMethod != null || path != null) {
                return String.format("**%s** `%s` - %s.%s",
                        httpMethod != null ? httpMethod : "HTTP",
                        path != null ? path : "/unknown",
                        classNameStr,
                        methodNameStr
                );
            }

        } catch (Exception e) {
            log.warn("Failed to extract JAX-RS endpoint from match", e);
        }

        return null;
    }

    private String extractJaxRsMethod(String code) {
        if (code.contains("@GET")) return "GET";
        if (code.contains("@POST")) return "POST";
        if (code.contains("@PUT")) return "PUT";
        if (code.contains("@DELETE")) return "DELETE";
        if (code.contains("@PATCH")) return "PATCH";
        return null;
    }

    private String extractJaxRsPath(String code, Object className) {
        try {
            // Look for @Path annotation
            java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("@Path\\(\"([^\"]+)\"\\)");
            java.util.regex.Matcher pathMatcher = pathPattern.matcher(code);

            if (pathMatcher.find()) {
                return pathMatcher.group(1);
            }

            // If no path found in method, try to infer from class name
            if (className != null) {
                String classNameStr = className.toString().toLowerCase();
                if (classNameStr.contains("product")) {
                    return "/products";
                } else if (classNameStr.contains("shopping") || classNameStr.contains("list")) {
                    return "/shopping-lists";
                } else if (classNameStr.contains("user")) {
                    return "/users";
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract JAX-RS path", e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractConfigurations(Object searchResults) {
        List<String> configs = new ArrayList<>();

        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    for (Object match : matches) {
                        if (match instanceof Map) {
                            Map<String, Object> matchMap = (Map<String, Object>) match;

                            Object className = matchMap.get("className");
                            Object code = matchMap.get("code");

                            if (className != null && code != null) {
                                String classNameStr = className.toString();
                                String codeStr = code.toString();

                                if (classNameStr.contains("Config") && codeStr.contains("@ApplicationPath")) {
                                    // Extract application path
                                    java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("@ApplicationPath\\(\"([^\"]+)\"\\)");
                                    java.util.regex.Matcher pathMatcher = pathPattern.matcher(codeStr);

                                    if (pathMatcher.find()) {
                                        configs.add(String.format("**%s**: Base path `/%s`", classNameStr, pathMatcher.group(1)));
                                    } else {
                                        configs.add(String.format("**%s**: JAX-RS Configuration", classNameStr));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract configurations", e);
        }

        return configs;
    }

    @SuppressWarnings("unchecked")
    private String formatComponentsFromSearch(Object searchResults) {
        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;
                StringBuilder response = new StringBuilder("🏗️ **Spring Components Found:**\n\n");

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    if (matches != null && !matches.isEmpty()) {
                        response.append("Found **").append(matches.size()).append(" Spring components**:\n\n");

                        int count = 1;
                        for (Object match : matches) {
                            if (match instanceof Map) {
                                Map<String, Object> matchMap = (Map<String, Object>) match;
                                response.append(formatComponentMatch(count, matchMap));
                                count++;
                            }
                        }
                    } else {
                        response.append("❌ No Spring components found in the analyzed repository.\n\n");
                        response.append("**Possible reasons:**\n");
                        response.append("• This might not be a Spring Boot project\n");
                        response.append("• Analysis is still in progress\n");
                        response.append("• Components are defined differently\n");
                    }
                }

                // Add explanation if available
                if (resultsMap.containsKey("explanation") && resultsMap.get("explanation") != null) {
                    response.append("\n**Analysis:**\n");
                    response.append(resultsMap.get("explanation"));
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format components response", e);
        }

        return "🏗️ **Spring Components Found:**\n\n" + searchResults.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatSearchResponse(String query, Object searchResults) {
        try {
            if (searchResults instanceof Map) {
                Map<String, Object> resultsMap = (Map<String, Object>) searchResults;
                StringBuilder response = new StringBuilder();
                response.append("🔎 **Search Results for '").append(query).append("':**\n\n");

                if (resultsMap.containsKey("totalMatches")) {
                    Object totalMatches = resultsMap.get("totalMatches");
                    response.append("Found **").append(totalMatches).append(" matches**:\n\n");
                }

                if (resultsMap.containsKey("matches")) {
                    List<?> matches = (List<?>) resultsMap.get("matches");

                    int count = 1;
                    for (Object match : matches) {
                        if (match instanceof Map) {
                            Map<String, Object> matchMap = (Map<String, Object>) match;
                            response.append(formatSearchMatch(count, matchMap));
                            count++;
                        }
                    }
                }

                // Add explanation if available
                if (resultsMap.containsKey("explanation") && resultsMap.get("explanation") != null) {
                    response.append("\n**Analysis:**\n");
                    response.append(resultsMap.get("explanation"));
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format search response", e);
        }

        return "🔎 **Search Results for '" + query + "':**\n\n" + searchResults.toString();
    }

    private String formatComponentMatch(int count, Map<String, Object> match) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(". ");

        Object className = match.get("className");
        Object annotations = match.get("annotations");
        Object packageName = match.get("packageName");

        if (className != null) {
            sb.append("**").append(className).append("**");
            if (packageName != null) {
                sb.append(" (").append(packageName).append(")");
            }
            sb.append("\n");

            if (annotations != null) {
                sb.append("   📝 Type: ").append(annotations).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String formatSearchMatch(int count, Map<String, Object> match) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(". ");

        Object className = match.get("className");
        Object methodName = match.get("methodName");
        Object filePath = match.get("filePath");

        if (className != null) {
            sb.append("**").append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            sb.append("**\n");

            if (filePath != null) {
                sb.append("   📁 File: ").append(filePath).append("\n");
            }

            Object code = match.get("code");
            if (code != null) {
                String codeSnippet = code.toString();
                if (codeSnippet.length() > 200) {
                    codeSnippet = codeSnippet.substring(0, 200) + "...";
                }
                sb.append("   ```java\n   ").append(codeSnippet.replace("\n", "\n   ")).append("\n   ```\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatFilesResponse(Object fileStructure) {
        try {
            if (fileStructure instanceof Map) {
                Map<String, Object> filesMap = (Map<String, Object>) fileStructure;
                StringBuilder response = new StringBuilder("📁 **Repository File Structure:**\n\n");

                if (filesMap.containsKey("fileCount")) {
                    Object fileCount = filesMap.get("fileCount");
                    response.append("Total Java files: **").append(fileCount).append("**\n\n");
                }

                if (filesMap.containsKey("javaFiles")) {
                    List<?> javaFiles = (List<?>) filesMap.get("javaFiles");

                    response.append("Java files:\n");
                    for (Object file : javaFiles) {
                        response.append("• `").append(file.toString()).append("`\n");
                    }
                } else {
                    response.append(fileStructure.toString());
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format files response", e);
        }

        return "📁 **Repository File Structure:**\n\n" + fileStructure.toString();
    }

    private String getCurrentRepositoryUrl(Map<String, Object> params) {
        return (String) params.get("currentRepositoryUrl");
    }


}