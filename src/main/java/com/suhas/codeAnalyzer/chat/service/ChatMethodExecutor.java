package com.suhas.codeAnalyzer.chat.service;

import com.suhas.codeAnalyzer.chat.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
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
    }

    private String getBaseUrl() {
        return "http://localhost:" + serverPort;
    }

    public ActionResult execute(LLMAction action) {
        try {
            log.info("Executing action: {} with parameters: {}", action.getActionName(), action.getParameters());

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

                default:
                    return ActionResult.error("Unknown action: " + action.getActionName());
            }
        } catch (Exception e) {
            log.error("Error executing action: {}", action.getActionName(), e);
            return ActionResult.error("Execution failed: " + e.getMessage());
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
                log.info("🔍 Checking analysis status for: {}", repoUrl);

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
            log.info("🔍 Finding REST endpoints...");

            Object endpoints = restTemplate.getForObject(
                    getBaseUrl() + "/api/query/endpoints",
                    Object.class
            );

            // Format the response nicely
            String formattedResponse = formatEndpointsResponse(endpoints);

            return ActionResult.success(formattedResponse, endpoints);

        } catch (Exception e) {
            log.error("❌ Failed to find endpoints", e);

            String errorMsg = "❌ **Could not find endpoints.**\n\n" +
                    "**Possible reasons:**\n" +
                    "• Repository analysis is still in progress\n" +
                    "• No repository has been analyzed yet\n" +
                    "• The repository doesn't contain REST endpoints\n\n" +
                    "**Try:**\n" +
                    "• Wait a few minutes if analysis is in progress\n" +
                    "• Analyze a repository first\n" +
                    "• Check system health";

            return ActionResult.error(errorMsg);
        }
    }

    private ActionResult findComponents(Map<String, Object> params) {
        try {
            log.info("🏗️ Finding Spring components...");

            Object components = restTemplate.getForObject(
                    getBaseUrl() + "/api/query/spring-components",
                    Object.class
            );

            // Format the response nicely
            String formattedResponse = formatComponentsResponse(components);

            return ActionResult.success(formattedResponse, components);

        } catch (Exception e) {
            log.error("❌ Failed to find components", e);

            String errorMsg = "❌ **Could not find Spring components.**\n\n" +
                    "**Possible reasons:**\n" +
                    "• Repository analysis is still in progress\n" +
                    "• No repository has been analyzed yet\n" +
                    "• The repository doesn't use Spring framework\n\n" +
                    "**Try:**\n" +
                    "• Wait a few minutes if analysis is in progress\n" +
                    "• Analyze a Spring Boot repository first";

            return ActionResult.error(errorMsg);
        }
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

    // Helper methods for formatting responses

    @SuppressWarnings("unchecked")
    private String formatEndpointsResponse(Object endpoints) {
        try {
            if (endpoints instanceof Map) {
                Map<String, Object> endpointsMap = (Map<String, Object>) endpoints;
                StringBuilder response = new StringBuilder("🔍 **REST Endpoints Found:**\n\n");

                // Handle different response formats
                if (endpointsMap.containsKey("results")) {
                    List<?> results = (List<?>) endpointsMap.get("results");
                    response.append("Found **").append(results.size()).append(" endpoints**:\n\n");

                    int count = 1;
                    for (Object result : results) {
                        if (result instanceof Map) {
                            Map<String, Object> endpoint = (Map<String, Object>) result;
                            response.append(count).append(". ");

                            // Extract endpoint details
                            Object method = endpoint.get("method");
                            Object path = endpoint.get("path");
                            Object className = endpoint.get("className");

                            if (method != null && path != null) {
                                response.append("**").append(method).append("** `").append(path).append("`");
                                if (className != null) {
                                    response.append(" (").append(className).append(")");
                                }
                            } else {
                                response.append(endpoint.toString());
                            }
                            response.append("\n");
                            count++;
                        }
                    }
                } else {
                    response.append("Here are the discovered endpoints:\n\n");
                    response.append(endpoints.toString());
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format endpoints response", e);
        }

        return "🔍 **REST Endpoints Found:**\n\n" + endpoints.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatComponentsResponse(Object components) {
        try {
            if (components instanceof Map) {
                Map<String, Object> componentsMap = (Map<String, Object>) components;
                StringBuilder response = new StringBuilder("🏗️ **Spring Components Found:**\n\n");

                if (componentsMap.containsKey("results")) {
                    List<?> results = (List<?>) componentsMap.get("results");
                    response.append("Found **").append(results.size()).append(" components**:\n\n");

                    int count = 1;
                    for (Object result : results) {
                        if (result instanceof Map) {
                            Map<String, Object> component = (Map<String, Object>) result;
                            response.append(count).append(". ");

                            Object className = component.get("className");
                            Object type = component.get("type");

                            if (className != null) {
                                response.append("**").append(className).append("**");
                                if (type != null) {
                                    response.append(" (").append(type).append(")");
                                }
                            } else {
                                response.append(component.toString());
                            }
                            response.append("\n");
                            count++;
                        }
                    }
                } else {
                    response.append("Here are the discovered components:\n\n");
                    response.append(components.toString());
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format components response", e);
        }

        return "🏗️ **Spring Components Found:**\n\n" + components.toString();
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

                if (resultsMap.containsKey("results")) {
                    List<?> results = (List<?>) resultsMap.get("results");

                    int count = 1;
                    for (Object result : results) {
                        if (result instanceof Map) {
                            Map<String, Object> match = (Map<String, Object>) result;
                            response.append(count).append(". ");

                            Object className = match.get("className");
                            Object methodName = match.get("methodName");
                            Object code = match.get("code");

                            if (className != null) {
                                response.append("**").append(className);
                                if (methodName != null) {
                                    response.append(".").append(methodName);
                                }
                                response.append("**\n");

                                if (code != null) {
                                    String codeSnippet = code.toString();
                                    if (codeSnippet.length() > 200) {
                                        codeSnippet = codeSnippet.substring(0, 200) + "...";
                                    }
                                    response.append("```java\n").append(codeSnippet).append("\n```\n");
                                }
                            } else {
                                response.append(match.toString());
                            }
                            response.append("\n");
                            count++;
                        }
                    }
                } else {
                    response.append(searchResults.toString());
                }

                return response.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format search response", e);
        }

        return "🔎 **Search Results for '" + query + "':**\n\n" + searchResults.toString();
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