package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class VectorStoreService {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreService.class);

    @Value("${external.chroma.base-url}")
    private String chromaBaseUrl;

    @Value("${external.chroma.collection-name}")
    private String collectionName;

    @Value("${external.chroma.timeout:10s}")
    private Duration timeout;

    @Value("${external.chroma.tenant:default_tenant}")
    private String tenant;

    @Value("${external.chroma.database:default_database}")
    private String database;

    // Store the collection UUID for operations
    private volatile String collectionId;

    private final WebClient webClient;

    public VectorStoreService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB for large responses
                .build();
    }

    // Request/Response DTOs for Chroma API
    public static class CreateCollectionRequest {
        private String name;
        private Map<String, Object> metadata;

        public CreateCollectionRequest(String name, Map<String, Object> metadata) {
            this.name = name;
            this.metadata = metadata;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class AddDocumentsRequest {
        private List<List<Double>> embeddings;
        private List<String> documents;
        private List<Map<String, Object>> metadatas;
        private List<String> ids;

        public AddDocumentsRequest() {
            this.embeddings = new ArrayList<>();
            this.documents = new ArrayList<>();
            this.metadatas = new ArrayList<>();
            this.ids = new ArrayList<>();
        }

        // Getters and setters
        public List<List<Double>> getEmbeddings() { return embeddings; }
        public void setEmbeddings(List<List<Double>> embeddings) { this.embeddings = embeddings; }
        public List<String> getDocuments() { return documents; }
        public void setDocuments(List<String> documents) { this.documents = documents; }
        public List<Map<String, Object>> getMetadatas() { return metadatas; }
        public void setMetadatas(List<Map<String, Object>> metadatas) { this.metadatas = metadatas; }
        public List<String> getIds() { return ids; }
        public void setIds(List<String> ids) { this.ids = ids; }
    }

    public static class QueryRequest {
        private List<List<Double>> query_embeddings;
        private Integer n_results;
        private Map<String, Object> where;

        public QueryRequest(List<List<Double>> queryEmbeddings, int nResults) {
            this.query_embeddings = queryEmbeddings;
            this.n_results = nResults;
        }

        // Getters and setters
        public List<List<Double>> getQuery_embeddings() { return query_embeddings; }
        public void setQuery_embeddings(List<List<Double>> query_embeddings) { this.query_embeddings = query_embeddings; }
        public Integer getN_results() { return n_results; }
        public void setN_results(Integer n_results) { this.n_results = n_results; }
        public Map<String, Object> getWhere() { return where; }
        public void setWhere(Map<String, Object> where) { this.where = where; }
    }

    public static class QueryResponse {
        private List<List<String>> ids;
        private List<List<String>> documents;
        private List<List<Map<String, Object>>> metadatas;
        private List<List<Double>> distances;

        // Getters and setters
        public List<List<String>> getIds() { return ids; }
        public void setIds(List<List<String>> ids) { this.ids = ids; }
        public List<List<String>> getDocuments() { return documents; }
        public void setDocuments(List<List<String>> documents) { this.documents = documents; }
        public List<List<Map<String, Object>>> getMetadatas() { return metadatas; }
        public void setMetadatas(List<List<Map<String, Object>>> metadatas) { this.metadatas = metadatas; }
        public List<List<Double>> getDistances() { return distances; }
        public void setDistances(List<List<Double>> distances) { this.distances = distances; }
    }

    public static class SearchResult {
        private String id;
        private String document;
        private Map<String, Object> metadata;
        private double distance;

        public SearchResult(String id, String document, Map<String, Object> metadata, double distance) {
            this.id = id;
            this.document = document;
            this.metadata = metadata;
            this.distance = distance;
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocument() { return document; }
        public void setDocument(String document) { this.document = document; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
    }

    /**
     * Initialize collection if it doesn't exist
     */
    public CompletableFuture<Boolean> initializeCollection() {
        logger.info("Initializing collection: {} in tenant: {} database: {}", collectionName, tenant, database);

        // Check if collection exists first
        return checkCollectionExists()
                .thenCompose(exists -> {
                    if (exists) {
                        logger.info("Collection {} already exists", collectionName);
                        return CompletableFuture.completedFuture(true);
                    } else {
                        return createCollection();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to initialize collection: {}", throwable.getMessage());
                    return false;
                });
    }

    /**
     * Check if collection exists and get its UUID
     */
    private CompletableFuture<Boolean> checkCollectionExists() {
        String collectionsUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections",
                chromaBaseUrl, tenant, database);

        logger.debug("Checking collections at: {}", collectionsUrl);

        return webClient.get()
                .uri(collectionsUrl)
                .retrieve()
                .bodyToMono(List.class)
                .timeout(timeout)
                .map(collections -> {
                    logger.debug("Checking {} collections for '{}':", collections.size(), collectionName);
                    for (Object col : collections) {
                        if (col instanceof Map) {
                            Map<String, Object> colMap = (Map<String, Object>) col;
                            if (collectionName.equals(colMap.get("name"))) {
                                // Store the collection UUID for later use
                                this.collectionId = (String) colMap.get("id");
                                logger.info("Found existing collection '{}' with UUID: {}", collectionName, collectionId);
                                return true;
                            }
                        }
                    }
                    logger.debug("Collection '{}' not found in existing collections", collectionName);
                    return false;
                })
                .doOnSuccess(exists -> logger.debug("Collection {} exists: {}", collectionName, exists))
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Create new collection and store its UUID
     */
    private CompletableFuture<Boolean> createCollection() {
        logger.info("Creating new collection: {} in tenant: {} database: {}", collectionName, tenant, database);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Java code repository analysis");

        CreateCollectionRequest request = new CreateCollectionRequest(collectionName, metadata);

        String createCollectionUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections",
                chromaBaseUrl, tenant, database);

        logger.debug("Creating collection at: {}", createCollectionUrl);

        return webClient.post()
                .uri(createCollectionUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(response -> {
                    // Extract and store the collection UUID
                    this.collectionId = (String) response.get("id");
                    logger.info("Collection creation response: {}", response);
                    logger.info("Successfully created collection '{}' with UUID: {}", collectionName, collectionId);
                    return true;
                })
                .doOnError(error -> {
                    logger.error("Failed to create collection: {}", error.getMessage());
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webError = (WebClientResponseException) error;
                        logger.error("HTTP Status: {}, Response: {}", webError.getStatusCode(), webError.getResponseBodyAsString());
                    }
                })
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Store embeddings in batches
     */
    public CompletableFuture<Boolean> storeEmbeddings(List<EmbeddingService.CodeEmbedding> embeddings) {
        if (embeddings.isEmpty()) {
            logger.warn("No embeddings provided to store");
            return CompletableFuture.completedFuture(true);
        }

        logger.info("Starting batch storage of {} embeddings in collection: {}", embeddings.size(), collectionName);

        // Process in batches to avoid overwhelming Chroma
        int batchSize = 50;
        List<List<EmbeddingService.CodeEmbedding>> batches = createBatches(embeddings, batchSize);
        logger.info("Created {} batches of max size {} for embedding storage", batches.size(), batchSize);

        return processBatches(batches, 0)
                .thenApply(results -> {
                    long successCount = results.stream().mapToLong(r -> r ? 1 : 0).sum();
                    logger.info("Successfully stored {}/{} embedding batches", successCount, results.size());
                    return successCount == results.size();
                });
    }

    private CompletableFuture<List<Boolean>> processBatches(List<List<EmbeddingService.CodeEmbedding>> batches, int currentBatch) {
        if (currentBatch >= batches.size()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        List<EmbeddingService.CodeEmbedding> batch = batches.get(currentBatch);
        logger.debug("Processing batch {}/{} with {} embeddings", currentBatch + 1, batches.size(), batch.size());

        return storeBatch(batch)
                .thenCompose(success -> {
                    // Small delay between batches
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return processBatches(batches, currentBatch + 1)
                            .thenApply(results -> {
                                results.add(0, success);
                                return results;
                            });
                });
    }

    private CompletableFuture<Boolean> storeBatch(List<EmbeddingService.CodeEmbedding> batch) {
        // Ensure we have a collection UUID
        if (collectionId == null) {
            logger.error("Collection UUID is null - cannot store embeddings");
            return CompletableFuture.completedFuture(false);
        }

        AddDocumentsRequest request = new AddDocumentsRequest();

        for (EmbeddingService.CodeEmbedding embedding : batch) {
            request.getEmbeddings().add(embedding.getEmbedding());
            request.getDocuments().add(embedding.getContent());
            request.getMetadatas().add(embedding.getMetadata());
            request.getIds().add(embedding.getChunkId());
        }

        // Use collection UUID instead of name
        String addDocumentsUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/add",
                chromaBaseUrl, tenant, database, collectionId);

        // Add debug logging
        logger.info("Storing batch of {} embeddings to collection UUID: {}", batch.size(), collectionId);
        logger.debug("First embedding dimension: {}", request.getEmbeddings().get(0).size());
        logger.debug("Request URL: {}", addDocumentsUrl);

        return webClient.post()
                .uri(addDocumentsUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout.multipliedBy(2))
                .map(response -> {
                    logger.info("Chroma response: {}", response);
                    return true;
                })
                .doOnSuccess(success -> logger.debug("Successfully stored batch of {} embeddings", batch.size()))
                .doOnError(error -> {
                    logger.error("Failed to store embedding batch - Error details: {}", error.getMessage());
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webError = (WebClientResponseException) error;
                        logger.error("HTTP Status: {}, Response Body: {}", webError.getStatusCode(), webError.getResponseBodyAsString());
                    }
                })
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Search for similar code chunks
     */
    public CompletableFuture<List<SearchResult>> searchSimilar(List<Double> queryEmbedding, int topK) {
        return searchSimilar(queryEmbedding, topK, null);
    }

    /**
     * Search for similar code chunks with filters
     */
    public CompletableFuture<List<SearchResult>> searchSimilar(List<Double> queryEmbedding, int topK, Map<String, Object> filters) {
        logger.debug("Searching for {} similar chunks in collection: {}", topK, collectionName);

        // Ensure collection is initialized before searching
        return ensureCollectionInitialized()
                .thenCompose(initialized -> {
                    if (!initialized) {
                        logger.error("Failed to initialize collection for search");
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    }

                    if (collectionId == null) {
                        logger.error("Collection UUID is null after initialization - cannot search");
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    }

                    QueryRequest request = new QueryRequest(List.of(queryEmbedding), topK);

                    // FIX: Only set filters if they're not null AND not empty
                    if (filters != null && !filters.isEmpty()) {
                        request.setWhere(filters);
                        logger.debug("Applying filters: {}", filters);
                    } else {
                        logger.debug("No filters applied - searching all documents");
                    }

                    String queryUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/query",
                            chromaBaseUrl, tenant, database, collectionId);

                    return webClient.post()
                            .uri(queryUrl)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(QueryResponse.class)
                            .timeout(timeout)
                            .map(this::convertToSearchResults)
                            .doOnSuccess(results -> logger.debug("Found {} similar chunks", results.size()))
                            .doOnError(error -> {
                                logger.error("Failed to search similar chunks: {}", error.getMessage());
                                if (error instanceof WebClientResponseException) {
                                    WebClientResponseException webError = (WebClientResponseException) error;
                                    logger.error("HTTP Status: {}, Response Body: {}", webError.getStatusCode(), webError.getResponseBodyAsString());
                                }
                            })
                            .onErrorReturn(new ArrayList<>())
                            .toFuture();
                });
    }

    /**
     * Ensure collection is initialized and UUID is available
     */
    private CompletableFuture<Boolean> ensureCollectionInitialized() {
        if (collectionId != null) {
            // Already initialized
            return CompletableFuture.completedFuture(true);
        }

        logger.info("Collection UUID not available, initializing collection...");
        return initializeCollection();
    }

    private List<SearchResult> convertToSearchResults(QueryResponse response) {
        List<SearchResult> results = new ArrayList<>();

        if (response.getIds() == null || response.getIds().isEmpty()) {
            return results;
        }

        List<String> ids = response.getIds().get(0);
        List<String> documents = response.getDocuments().get(0);
        List<Map<String, Object>> metadatas = response.getMetadatas().get(0);
        List<Double> distances = response.getDistances().get(0);

        for (int i = 0; i < ids.size(); i++) {
            SearchResult result = new SearchResult(
                    ids.get(i),
                    documents.get(i),
                    metadatas.get(i),
                    distances.get(i)
            );
            results.add(result);
        }

        logger.debug("Converted {} search results", results.size());
        return results;
    }

    /**
     * Get collection statistics
     */
    public CompletableFuture<Map> getCollectionStats() {
        return ensureCollectionInitialized()
                .thenCompose(initialized -> {
                    if (!initialized || collectionId == null) {
                        logger.warn("Collection not initialized - cannot get stats");
                        return CompletableFuture.completedFuture(new HashMap<>());
                    }

                    String statsUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s",
                            chromaBaseUrl, tenant, database, collectionId);

                    return webClient.get()
                            .uri(statsUrl)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(timeout)
                            .doOnSuccess(stats -> logger.debug("Retrieved collection stats: {}", stats))
                            .doOnError(error -> logger.error("Failed to get collection stats: {}", error.getMessage()))
                            .onErrorReturn(new HashMap<>())
                            .toFuture();
                });
    }

    /**
     * Delete collection (for cleanup)
     */
    public CompletableFuture<Boolean> deleteCollection() {
        logger.info("Deleting collection: {}", collectionName);

        if (collectionId == null) {
            logger.warn("Collection UUID is null - cannot delete");
            return CompletableFuture.completedFuture(false);
        }

        String deleteUrl = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s",
                chromaBaseUrl, tenant, database, collectionId);

        return webClient.delete()
                .uri(deleteUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(response -> true)
                .doOnSuccess(success -> logger.info("Successfully deleted collection: {}", collectionName))
                .doOnError(error -> logger.error("Failed to delete collection {}: {}", collectionName, error.getMessage()))
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Health check for Chroma service
     */
    public CompletableFuture<Boolean> healthCheck() {
        return webClient.get()
                .uri(chromaBaseUrl + "/api/v2/heartbeat")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> response.containsKey("nanosecond heartbeat"))
                .doOnSuccess(healthy -> logger.debug("Chroma health check: {}", healthy ? "healthy" : "unhealthy"))
                .doOnError(error -> logger.warn("Chroma health check failed: {}", error.getMessage()))
                .onErrorReturn(false)
                .toFuture();
    }

    // Helper methods
    private <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, end));
        }
        return batches;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getChromaUrl() {
        return chromaBaseUrl;
    }
}