package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private LLMService llmService;

    public static class QueryRequest {
        private String query;
        private int maxResults = 5;
        private boolean includeCode = true;
        private boolean includeExplanation = true;
        private Map<String, Object> filters;

        public QueryRequest() {
            this.filters = new HashMap<>();
        }

        // Getters and setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
        public boolean isIncludeCode() { return includeCode; }
        public void setIncludeCode(boolean includeCode) { this.includeCode = includeCode; }
        public boolean isIncludeExplanation() { return includeExplanation; }
        public void setIncludeExplanation(boolean includeExplanation) { this.includeExplanation = includeExplanation; }
        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
    }

    public static class QueryResponse {
        private String query;
        private String explanation;
        private List<CodeMatch> matches;
        private int totalMatches;
        private long processingTimeMs;
        private Map<String, Object> metadata;

        public QueryResponse() {
            this.matches = new ArrayList<>();
            this.metadata = new HashMap<>();
        }

        // Getters and setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public List<CodeMatch> getMatches() { return matches; }
        public void setMatches(List<CodeMatch> matches) { this.matches = matches; }
        public int getTotalMatches() { return totalMatches; }
        public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class CodeMatch {
        private String id;
        private String code;
        private double similarity;
        private String type;
        private String className;
        private String methodName;
        private String packageName;
        private String filePath;
        private List<String> annotations;
        private Map<String, Object> metadata;

        public CodeMatch() {
            this.annotations = new ArrayList<>();
            this.metadata = new HashMap<>();
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public List<String> getAnnotations() { return annotations; }
        public void setAnnotations(List<String> annotations) { this.annotations = annotations; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * Main RAG query method - searches code and generates explanation
     */
    public CompletableFuture<QueryResponse> searchCode(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        logger.info("Processing query: {}", request.getQuery());

        return embeddingService.generateQueryEmbedding(request.getQuery())
                .thenCompose(queryEmbedding -> {
                    if (queryEmbedding.isEmpty()) {
                        throw new RuntimeException("Failed to generate query embedding");
                    }

                    // Search for similar code
                    return vectorStoreService.searchSimilar(queryEmbedding, request.getMaxResults(), request.getFilters());
                })
                .thenCompose(searchResults -> {
                    // Convert search results to CodeMatch objects
                    List<CodeMatch> matches = convertSearchResults(searchResults);

                    QueryResponse response = new QueryResponse();
                    response.setQuery(request.getQuery());
                    response.setMatches(matches);
                    response.setTotalMatches(matches.size());

                    if (request.isIncludeExplanation() && !matches.isEmpty()) {
                        // Generate explanation using LLM
                        String codeContext = buildCodeContext(matches);
                        return llmService.generateCodeAnalysisResponse(request.getQuery(), codeContext)
                                .thenApply(explanation -> {
                                    response.setExplanation(explanation);
                                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                                    return response;
                                });
                    } else {
                        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                        return CompletableFuture.completedFuture(response);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error processing query '{}': {}", request.getQuery(), throwable.getMessage());

                    QueryResponse errorResponse = new QueryResponse();
                    errorResponse.setQuery(request.getQuery());
                    errorResponse.setExplanation("Sorry, I encountered an error while searching the code: " + throwable.getMessage());
                    errorResponse.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                    return errorResponse;
                });
    }

    /**
     * Search for specific code patterns (controllers, services, etc.)
     */
    public CompletableFuture<QueryResponse> searchByType(String codeType, int maxResults) {
        QueryRequest request = new QueryRequest();
        request.setQuery("Find " + codeType + " code");
        request.setMaxResults(maxResults);
        request.setIncludeExplanation(false);

        // Fix: Ensure the filter value matches exactly what's stored
        Map<String, Object> filters = new HashMap<>();
        filters.put("type", codeType); // Should be "CLASS" not "class"
        request.setFilters(filters);

        return searchCode(request);
    }

    /**
     * Search for Spring Boot components
     */
    public CompletableFuture<QueryResponse> searchSpringComponents(int maxResults) {
        QueryRequest request = new QueryRequest();
        request.setQuery("Find Spring Boot components");
        request.setMaxResults(maxResults);
        request.setIncludeExplanation(false);

        // Add Spring component filter
        Map<String, Object> filters = new HashMap<>();
        filters.put("isSpringComponent", true);
        request.setFilters(filters);

        return searchCode(request);
    }

    /**
     * Find API endpoints in the codebase
     */
    public CompletableFuture<QueryResponse> findApiEndpoints() {
        QueryRequest request = new QueryRequest();
        request.setQuery("What REST API endpoints are available?");
        request.setMaxResults(10);
        request.setIncludeExplanation(true);

        // Filter for endpoints
        Map<String, Object> filters = new HashMap<>();
        filters.put("isEndpoint", true);
        request.setFilters(filters);

        return searchCode(request)
                .thenCompose(response -> {
                    if (!response.getMatches().isEmpty()) {
                        String endpointContext = buildCodeContext(response.getMatches());
                        return llmService.analyzeApiEndpoints(endpointContext)
                                .thenApply(analysis -> {
                                    response.setExplanation(analysis);
                                    return response;
                                });
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    /**
     * Analyze business logic flow
     */
    public CompletableFuture<QueryResponse> analyzeBusinessLogic(String query) {
        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setMaxResults(8);
        request.setIncludeExplanation(true);

        return searchCode(request)
                .thenCompose(response -> {
                    if (!response.getMatches().isEmpty()) {
                        String codeContext = buildCodeContext(response.getMatches());
                        return llmService.analyzeBusinessLogic(query, codeContext)
                                .thenApply(analysis -> {
                                    response.setExplanation(analysis);
                                    return response;
                                });
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    /**
     * Convert VectorStore search results to CodeMatch objects
     */
    private List<CodeMatch> convertSearchResults(List<VectorStoreService.SearchResult> searchResults) {
        return searchResults.stream()
                .map(result -> {
                    CodeMatch match = new CodeMatch();
                    match.setId(result.getId());
                    match.setCode(result.getDocument());
                    match.setSimilarity(1.0 - result.getDistance()); // Convert distance to similarity
                    match.setMetadata(result.getMetadata());

                    // Extract metadata into specific fields
                    Map<String, Object> metadata = result.getMetadata();
                    if (metadata != null) {
                        match.setType((String) metadata.get("type"));
                        match.setClassName((String) metadata.get("className"));
                        match.setMethodName((String) metadata.get("methodName"));
                        match.setPackageName((String) metadata.get("packageName"));
                        match.setFilePath((String) metadata.get("filePath"));

                        String annotations = (String) metadata.get("annotations");
                        if (annotations != null && !annotations.isEmpty()) {
                            match.setAnnotations(Arrays.asList(annotations.split(",")));
                        }
                    }

                    return match;
                })
                .collect(Collectors.toList());
    }

    /**
     * Build code context for LLM from matched code snippets
     */
    private String buildCodeContext(List<CodeMatch> matches) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < matches.size() && i < 5; i++) { // Limit to top 5 matches
            CodeMatch match = matches.get(i);
            context.append("// ").append(match.getType())
                    .append(" from ").append(match.getClassName())
                    .append(" (similarity: ").append(String.format("%.2f", match.getSimilarity())).append(")\n");
            context.append(match.getCode()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * Get query suggestions based on the codebase
     */
    public List<String> getQuerySuggestions() {
        return Arrays.asList(
                "What REST endpoints are available?",
                "Show me the controller classes",
                "How is user authentication handled?",
                "What services are available?",
                "Show me database operations",
                "What are the main business logic flows?",
                "How does the application handle errors?",
                "What external APIs does this application call?",
                "Show me the data models",
                "How is dependency injection configured?"
        );
    }

    /**
     * Get search statistics
     */
    public CompletableFuture<Map<String, Object>> getSearchStats() {
        return vectorStoreService.getCollectionStats()
                .thenApply(stats -> {
                    Map<String, Object> searchStats = new HashMap<>();
                    searchStats.put("totalDocuments", stats.getOrDefault("documents_count", 0));
                    searchStats.put("collectionInfo", stats);
                    searchStats.put("availableFilters", Arrays.asList("type", "className", "isSpringComponent", "isEndpoint"));
                    searchStats.put("supportedTypes", Arrays.asList("CLASS", "METHOD", "INTERFACE"));
                    return searchStats;
                });
    }
}