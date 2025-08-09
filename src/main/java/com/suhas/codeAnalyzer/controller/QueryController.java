package com.suhas.codeAnalyzer.controller;

import com.suhas.codeAnalyzer.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);

    @Autowired
    private QueryService queryService;

    /**
     * Main code search endpoint
     * POST /api/query/search
     */
    @PostMapping("/search")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> searchCode(@RequestBody QueryService.QueryRequest request) {
        logger.info("Received code search query: {}", request.getQuery());

        return queryService.searchCode(request)
                .thenApply(response -> {
                    if (response.getTotalMatches() > 0) {
                        logger.info("Found {} matches for query: {}", response.getTotalMatches(), request.getQuery());
                        return ResponseEntity.ok(response);
                    } else {
                        logger.info("No matches found for query: {}", request.getQuery());
                        return ResponseEntity.ok(response);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error processing search query: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery(request.getQuery());
                    errorResponse.setExplanation("An error occurred while searching: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Simple text query endpoint
     * GET /api/query/ask?q=what controllers are available
     */
    @GetMapping("/ask")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> askQuestion(@RequestParam String q) {
        logger.info("Received question: {}", q);

        QueryService.QueryRequest request = new QueryService.QueryRequest();
        request.setQuery(q);
        request.setMaxResults(5);
        request.setIncludeExplanation(true);

        return queryService.searchCode(request)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error processing question: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery(q);
                    errorResponse.setExplanation("Sorry, I couldn't process your question: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Find specific code types
     * GET /api/query/type/{type}?limit=10
     */
    @GetMapping("/type/{type}")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> findByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "10") int limit) {

        logger.info("Searching for code type: {} with limit: {}", type, limit);

        return queryService.searchByType(type, limit)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error searching by type {}: {}", type, throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery("Find " + type + " code");
                    errorResponse.setExplanation("Error searching for " + type + ": " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Find Spring Boot components
     * GET /api/query/spring-components?limit=10
     */
    @GetMapping("/spring-components")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> findSpringComponents(
            @RequestParam(defaultValue = "10") int limit) {

        logger.info("Searching for Spring Boot components with limit: {}", limit);

        return queryService.searchSpringComponents(limit)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error searching Spring components: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery("Find Spring Boot components");
                    errorResponse.setExplanation("Error finding Spring components: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Find API endpoints
     * GET /api/query/endpoints
     */
    @GetMapping("/endpoints")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> findApiEndpoints() {
        logger.info("Searching for API endpoints");

        return queryService.findApiEndpoints()
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error finding API endpoints: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery("Find API endpoints");
                    errorResponse.setExplanation("Error finding API endpoints: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Analyze business logic
     * POST /api/query/business-logic
     */
    @PostMapping("/business-logic")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> analyzeBusinessLogic(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        logger.info("Analyzing business logic for query: {}", query);

        return queryService.analyzeBusinessLogic(query)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error analyzing business logic: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery(query);
                    errorResponse.setExplanation("Error analyzing business logic: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Get query suggestions
     * GET /api/query/suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getQuerySuggestions() {
        logger.debug("Retrieving query suggestions");

        List<String> suggestions = queryService.getQuerySuggestions();

        Map<String, Object> response = new HashMap<>();
        response.put("suggestions", suggestions);
        response.put("total", suggestions.size());
        response.put("categories", Map.of(
                "endpoints", "What REST endpoints are available?",
                "components", "Show me the controller classes",
                "services", "What services are available?",
                "business_logic", "What are the main business logic flows?",
                "data", "Show me database operations"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get search statistics
     * GET /api/query/stats
     */
    @GetMapping("/stats")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getSearchStats() {
        logger.debug("Retrieving search statistics");

        return queryService.getSearchStats()
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error retrieving search stats: {}", throwable.getMessage());
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Failed to retrieve statistics");
                    errorResponse.put("message", throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Advanced search with filters
     * POST /api/query/advanced
     */
    @PostMapping("/advanced")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> advancedSearch(@RequestBody AdvancedSearchRequest request) {
        logger.info("Advanced search: query='{}', filters={}", request.getQuery(), request.getFilters());

        QueryService.QueryRequest queryRequest = new QueryService.QueryRequest();
        queryRequest.setQuery(request.getQuery());
        queryRequest.setMaxResults(request.getMaxResults());
        queryRequest.setIncludeCode(request.isIncludeCode());
        queryRequest.setIncludeExplanation(request.isIncludeExplanation());
        queryRequest.setFilters(request.getFilters());

        return queryService.searchCode(queryRequest)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error in advanced search: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery(request.getQuery());
                    errorResponse.setExplanation("Advanced search failed: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    /**
     * Search within specific file or package
     * GET /api/query/scope?q=find controllers&scope=com.example.controller
     */
    @GetMapping("/scope")
    public CompletableFuture<ResponseEntity<QueryService.QueryResponse>> searchInScope(
            @RequestParam String q,
            @RequestParam String scope) {

        logger.info("Scoped search: query='{}', scope='{}'", q, scope);

        QueryService.QueryRequest request = new QueryService.QueryRequest();
        request.setQuery(q);
        request.setMaxResults(10);
        request.setIncludeExplanation(true);

        // Add scope filter
        Map<String, Object> filters = new HashMap<>();
        if (scope.contains(".")) {
            filters.put("packageName", scope);
        } else {
            filters.put("className", scope);
        }
        request.setFilters(filters);

        return queryService.searchCode(request)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    logger.error("Error in scoped search: {}", throwable.getMessage());
                    QueryService.QueryResponse errorResponse = new QueryService.QueryResponse();
                    errorResponse.setQuery(q);
                    errorResponse.setExplanation("Scoped search failed: " + throwable.getMessage());
                    return ResponseEntity.status(500).body(errorResponse);
                });
    }

    // DTO for advanced search
    public static class AdvancedSearchRequest {
        private String query;
        private int maxResults = 5;
        private boolean includeCode = true;
        private boolean includeExplanation = true;
        private Map<String, Object> filters = new HashMap<>();

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
}