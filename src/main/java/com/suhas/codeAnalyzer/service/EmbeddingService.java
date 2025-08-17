package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${external.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${external.ollama.embedding-model}")
    private String embeddingModel;

    @Value("${external.ollama.timeout:30s}")
    private Duration timeout;

    private final WebClient webClient;

    public EmbeddingService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    public static class EmbeddingRequest {
        private String model;
        private String prompt;

        public EmbeddingRequest(String model, String prompt) {
            this.model = model;
            this.prompt = prompt;
        }

        // Getters and setters
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }

    public static class EmbeddingResponse {
        private List<Double> embedding;

        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
    }

    public static class CodeEmbedding {
        private String chunkId;
        private List<Double> embedding;
        private String content;
        private Map<String, Object> metadata;

        public CodeEmbedding(String chunkId, List<Double> embedding, String content, Map<String, Object> metadata) {
            this.chunkId = chunkId;
            this.embedding = embedding;
            this.content = content;
            this.metadata = metadata;
        }

        // Getters and setters
        public String getChunkId() { return chunkId; }
        public void setChunkId(String chunkId) { this.chunkId = chunkId; }
        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * Generate embedding for a single text
     */
    public CompletableFuture<List<Double>> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.debug("Skipping embedding generation for empty text");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // Truncate if too long (embedding models have limits)
        String processedText = text.length() > 8000 ? text.substring(0, 8000) + "..." : text;
        if (text.length() > 8000) {
            logger.debug("Text truncated from {} to {} characters for embedding", text.length(), processedText.length());
        }

        logger.debug("Creating embedding request for text length: {} using model: {}", processedText.length(), embeddingModel);
        EmbeddingRequest request = new EmbeddingRequest(embeddingModel, processedText);

        return webClient.post()
                .uri(ollamaBaseUrl + "/api/embeddings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest)))
                .map(EmbeddingResponse::getEmbedding)
                .doOnSuccess(embedding -> logger.debug("Successfully generated embedding for text of length: {}, embedding dimension: {}",
                        processedText.length(), embedding != null ? embedding.size() : 0))
                .doOnError(error -> logger.error("Failed to generate embedding for text of length {}: {}", processedText.length(), error.getMessage()))
                .toFuture();
    }

    /**
     * Generate embeddings for multiple code chunks in batches
     */
    public CompletableFuture<List<CodeEmbedding>> generateEmbeddings(List<CodeParserService.CodeChunk> codeChunks) {
        logger.info("Starting batch embedding generation for {} code chunks", codeChunks.size());

        // Process in batches to avoid overwhelming the service
        int batchSize = 5;
        List<List<CodeParserService.CodeChunk>> batches = createBatches(codeChunks, batchSize);
        logger.debug("Created {} batches of size {} for processing", batches.size(), batchSize);

        return Flux.fromIterable(batches)
                .flatMap(batch -> processBatch(batch), 2) // Process 2 batches concurrently
                .collectList()
                .map(this::flattenBatchResults)
                .doOnSuccess(embeddings -> {
                    logger.info("Successfully generated {} embeddings out of {} requested chunks", embeddings.size(), codeChunks.size());
                    if (embeddings.size() < codeChunks.size()) {
                        logger.warn("Some embeddings failed to generate: {} successful out of {} total", embeddings.size(), codeChunks.size());
                    }
                })
                .doOnError(error -> logger.error("Batch embedding generation failed: {}", error.getMessage()))
                .toFuture();
    }

    private Mono<List<CodeEmbedding>> processBatch(List<CodeParserService.CodeChunk> batch) {
        logger.debug("Processing embedding batch of {} chunks", batch.size());
        long startTime = System.currentTimeMillis();

        return Flux.fromIterable(batch)
                .flatMap(chunk -> {
                    return Mono.fromFuture(generateEmbedding(chunk.getContent()))
                            .map(embedding -> new CodeEmbedding(
                                    chunk.getId(),
                                    embedding,
                                    chunk.getContent(),
                                    createMetadataForChunk(chunk)
                            ))
                            .onErrorResume(error -> {
                                logger.warn("Failed to generate embedding for chunk {} (type: {}, class: {}): {}", 
                                    chunk.getId(), chunk.getType(), chunk.getClassName(), error.getMessage());
                                return Mono.empty(); // Skip failed chunks
                            });
                })
                .collectList()
                .doOnNext(results -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.debug("Batch processing completed in {}ms, generated {} embeddings", duration, results.size());
                })
                .delayElement(Duration.ofMillis(500)); // Small delay between batches
    }

    private List<List<CodeParserService.CodeChunk>> createBatches(List<CodeParserService.CodeChunk> chunks, int batchSize) {
        logger.debug("Creating batches from {} chunks with batch size {}", chunks.size(), batchSize);
        List<List<CodeParserService.CodeChunk>> batches = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            batches.add(chunks.subList(i, end));
        }
        logger.debug("Created {} batches for processing", batches.size());
        return batches;
    }

    private List<CodeEmbedding> flattenBatchResults(List<List<CodeEmbedding>> batchResults) {
        return batchResults.stream()
                .flatMap(List::stream)
                .toList();
    }

    private Map<String, Object> createMetadataForChunk(CodeParserService.CodeChunk chunk) {
        logger.trace("Creating metadata for chunk: {} (type: {})", chunk.getId(), chunk.getType());
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());

        // Add basic chunk information
        metadata.put("chunkId", chunk.getId());
        metadata.put("type", chunk.getType());
        metadata.put("className", chunk.getClassName());
        metadata.put("packageName", chunk.getPackageName());
        metadata.put("filePath", chunk.getFilePath());
        metadata.put("contentLength", chunk.getContent().length());
        metadata.put("hasAnnotations", !chunk.getAnnotations().isEmpty());

        // Add Spring-specific metadata
        if (chunk.getAnnotations() != null) {
            metadata.put("annotations", String.join(",", chunk.getAnnotations()));
            metadata.put("isSpringComponent", isSpringComponent(chunk.getAnnotations()));
        }

        // Add method-specific metadata
        if ("METHOD".equals(chunk.getType()) && chunk.getMethodName() != null) {
            metadata.put("methodName", chunk.getMethodName());
            metadata.put("fullMethodName", chunk.getClassName() + "." + chunk.getMethodName());
        }

        return metadata;
    }

    private boolean isSpringComponent(List<String> annotations) {
        return annotations.stream()
                .anyMatch(ann -> ann.contains("@Controller") ||
                        ann.contains("@Service") ||
                        ann.contains("@Repository") ||
                        ann.contains("@Component") ||
                        ann.contains("@Entity"));
    }

    /**
     * Generate embedding for a query (for searching)
     */
    public CompletableFuture<List<Double>> generateQueryEmbedding(String query) {
        logger.info("Generating embedding for search query: '{}'", query);

        // Add context to help with code-related queries
        String enhancedQuery = "Java Spring Boot code: " + query;

        return generateEmbedding(enhancedQuery)
                .whenComplete((embedding, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to generate query embedding for '{}': {}", query, throwable.getMessage());
                    } else {
                        logger.info("Successfully generated query embedding with dimension: {} for query: '{}'", embedding.size(), query);
                    }
                });
    }

    /**
     * Health check for embedding service
     */
    public CompletableFuture<Boolean> healthCheck() {
        logger.debug("Performing embedding service health check");
        return generateEmbedding("test")
                .thenApply(embedding -> embedding != null && !embedding.isEmpty())
                .exceptionally(throwable -> {
                    logger.error("Embedding service health check failed: {}", throwable.getMessage());
                    return false;
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        logger.info("Embedding service health check completed: {}", result ? "HEALTHY" : "UNHEALTHY");
                    }
                });
    }

    /**
     * Get embedding model info
     */
    public String getEmbeddingModelInfo() {
        return String.format("Model: %s, Service: %s", embeddingModel, ollamaBaseUrl);
    }
}