package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private int n_results;
        private Map<String, Object> where;

        public QueryRequest(List<List<Double>> queryEmbeddings, int nResults) {
            this.query_embeddings = queryEmbeddings;
            this.n_results = nResults;
        }

        // Getters and setters
        public List<List<Double>> getQuery_embeddings() { return query_embeddings; }
        public void setQuery_embeddings(List<List<Double>> query_embeddings) { this.query_embeddings = query_embeddings; }
        public int getN_results() { return n_results; }
        public void setN_results(int n_results) { this.n_results = n_results; }
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
        logger.info("Initializing collection: {}", collectionName);

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
     * Check if collection exists
     */
    private CompletableFuture<Boolean> checkCollectionExists() {
        return webClient.get()
                .uri(chromaBaseUrl + "/api/v2/collections")
                .retrieve()
                .bodyToMono(List.class)
                .timeout(timeout)
                .map(collections -> {
                    return collections.stream()
                            .anyMatch(col -> {
                                if (col instanceof Map) {
                                    Map<String, Object> colMap = (Map<String, Object>) col;
                                    return collectionName.equals(colMap.get("name"));
                                }
                                return false;
                            });
                })
                .doOnSuccess(exists -> logger.debug("Collection {} exists: {}", collectionName, exists))
                .toFuture();
    }

    /**
     * Create new collection
     */
    private CompletableFuture<Boolean> createCollection() {
        logger.info("Creating new collection: {}", collectionName);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Java code repository analysis");
        metadata.put("created_at", System.currentTimeMillis());

        CreateCollectionRequest request = new CreateCollectionRequest(collectionName, metadata);

        return webClient.post()
                .uri(chromaBaseUrl + "/api/v2/collections")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(response -> true)
                .doOnSuccess(success -> logger.info("Successfully created collection: {}", collectionName))
                .doOnError(error -> logger.error("Failed to create collection {}: {}", collectionName, error.getMessage()))
                .toFuture();
    }

    /**
     * Store embeddings in batches
     */
    public CompletableFuture<Boolean> storeEmbeddings(List<EmbeddingService.CodeEmbedding> embeddings) {
        if (embeddings.isEmpty()) {
            logger.warn("No embeddings to store");
            return CompletableFuture.completedFuture(true);
        }

        logger.info("Storing {} embeddings in collection: {}", embeddings.size(), collectionName);

        // Process in batches to avoid overwhelming Chroma
        int batchSize = 50;
        List<List<EmbeddingService.CodeEmbedding>> batches = createBatches(embeddings, batchSize);

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
        AddDocumentsRequest request = new AddDocumentsRequest();

        for (EmbeddingService.CodeEmbedding embedding : batch) {
            request.getEmbeddings().add(embedding.getEmbedding());
            request.getDocuments().add(embedding.getContent());
            request.getMetadatas().add(embedding.getMetadata());
            request.getIds().add(embedding.getChunkId());
        }

        return webClient.post()
                .uri(chromaBaseUrl + "/api/v2/collections/" + collectionName + "/add")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout.multipliedBy(2)) // Longer timeout for batch operations
                .map(response -> true)
                .doOnSuccess(success -> logger.debug("Successfully stored batch of {} embeddings", batch.size()))
                .doOnError(error -> logger.error("Failed to store embedding batch: {}", error.getMessage()))
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

        QueryRequest request = new QueryRequest(List.of(queryEmbedding), topK);
        if (filters != null) {
            request.setWhere(filters);
        }

        return webClient.post()
                .uri(chromaBaseUrl + "/api/v2/collections/" + collectionName + "/query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .timeout(timeout)
                .map(this::convertToSearchResults)
                .doOnSuccess(results -> logger.debug("Found {} similar chunks", results.size()))
                .doOnError(error -> logger.error("Failed to search similar chunks: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>())
                .toFuture();
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

        return results;
    }

    /**
     * Get collection statistics
     */
    public CompletableFuture<Map> getCollectionStats() {
        return webClient.get()
                .uri(chromaBaseUrl + "/api/v2/collections/" + collectionName)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .doOnSuccess(stats -> logger.debug("Retrieved collection stats: {}", stats))
                .doOnError(error -> logger.error("Failed to get collection stats: {}", error.getMessage()))
                .onErrorReturn(new HashMap<>())
                .toFuture();
    }

    /**
     * Delete collection (for cleanup)
     */
    public CompletableFuture<Boolean> deleteCollection() {
        logger.info("Deleting collection: {}", collectionName);

        return webClient.delete()
                .uri(chromaBaseUrl + "/api/v2/collections/" + collectionName)
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