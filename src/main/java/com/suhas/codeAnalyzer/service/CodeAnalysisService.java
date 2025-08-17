package com.suhas.codeAnalyzer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(CodeAnalysisService.class);

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private CodeParserService codeParserService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    // Track analysis progress for each repository
    private final Map<String, AnalysisProgress> analysisProgressMap = new ConcurrentHashMap<>();

    public enum AnalysisStatus {
        INITIALIZING, PARSING, GENERATING_EMBEDDINGS, STORING_VECTORS, COMPLETED, FAILED
    }

    public static class AnalysisProgress {
        private AnalysisStatus status;
        private String message;
        private int totalFiles;
        private int processedFiles;
        private int totalChunks;
        private int processedChunks;
        private long startTime;
        private long endTime;
        private String error;

        public AnalysisProgress() {
            this.startTime = System.currentTimeMillis();
        }

        // Getters and setters
        public AnalysisStatus getStatus() { return status; }
        public void setStatus(AnalysisStatus status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(int processedFiles) { this.processedFiles = processedFiles; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public int getProcessedChunks() { return processedChunks; }
        public void setProcessedChunks(int processedChunks) { this.processedChunks = processedChunks; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public double getProgressPercentage() {
            if (totalChunks == 0) return 0.0;
            return (double) processedChunks / totalChunks * 100.0;
        }

        public long getDurationMs() {
            long end = endTime > 0 ? endTime : System.currentTimeMillis();
            return end - startTime;
        }
    }

    public static class AnalysisResult {
        private String repositoryUrl;
        private AnalysisStatus status;
        private int totalChunks;
        private int storedEmbeddings;
        private long processingTimeMs;
        private Map<String, Integer> chunksByType;
        private List<String> errors;

        public AnalysisResult() {
            this.chunksByType = new HashMap<>();
            this.errors = new ArrayList<>();
        }

        // Getters and setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        public AnalysisStatus getStatus() { return status; }
        public void setStatus(AnalysisStatus status) { this.status = status; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public int getStoredEmbeddings() { return storedEmbeddings; }
        public void setStoredEmbeddings(int storedEmbeddings) { this.storedEmbeddings = storedEmbeddings; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        public Map<String, Integer> getChunksByType() { return chunksByType; }
        public void setChunksByType(Map<String, Integer> chunksByType) { this.chunksByType = chunksByType; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    /**
     * Start complete analysis of a repository
     */
    public CompletableFuture<AnalysisResult> analyzeRepository(String repositoryUrl) {
        String repoId = generateRepositoryId(repositoryUrl);

        AnalysisProgress progress = new AnalysisProgress();
        progress.setStatus(AnalysisStatus.INITIALIZING);
        progress.setMessage("Starting repository analysis");
        analysisProgressMap.put(repoId, progress);

        logger.info("Starting complete analysis pipeline for repository: {} (ID: {})", repositoryUrl, repoId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure vector store is ready
                logger.info("Phase 1: Initializing vector store for repository: {}", repositoryUrl);
                progress.setStatus(AnalysisStatus.INITIALIZING);
                progress.setMessage("Initializing vector store");
                vectorStoreService.initializeCollection().join();
                logger.debug("Vector store initialization completed");

                // Get repository status (should already be cloned)
                logger.debug("Checking repository status for: {}", repositoryUrl);
                RepositoryService.RepositoryStatus repoStatus = repositoryService.getRepositoryStatus(repositoryUrl);
                if (repoStatus == null || repoStatus.getLocalPath() == null) {
                    logger.error("Repository not found or not cloned: {}", repositoryUrl);
                    throw new RuntimeException("Repository not found or not cloned: " + repositoryUrl);
                }
                logger.debug("Repository found at local path: {}", repoStatus.getLocalPath());

                // Step 1: Parse repository
                logger.info("Phase 2: Parsing Java files for repository: {}", repositoryUrl);
                progress.setStatus(AnalysisStatus.PARSING);
                progress.setMessage("Parsing Java files");
                List<CodeParserService.CodeChunk> codeChunks = codeParserService.parseRepository(repoStatus.getLocalPath());

                progress.setTotalChunks(codeChunks.size());
                progress.setMessage(String.format("Found %d code chunks", codeChunks.size()));
                logger.info("Code parsing completed: {} chunks extracted", codeChunks.size());

                if (codeChunks.isEmpty()) {
                    logger.error("No Java code chunks found in repository: {}", repositoryUrl);
                    throw new RuntimeException("No Java code chunks found in repository");
                }

                // Step 2: Generate embeddings
                logger.info("Phase 3: Generating embeddings for {} chunks from repository: {}", codeChunks.size(), repositoryUrl);
                progress.setStatus(AnalysisStatus.GENERATING_EMBEDDINGS);
                progress.setMessage("Generating embeddings");
                List<EmbeddingService.CodeEmbedding> embeddings = embeddingService.generateEmbeddings(codeChunks).join();

                progress.setProcessedChunks(embeddings.size());
                progress.setMessage(String.format("Generated %d embeddings", embeddings.size()));
                logger.info("Embedding generation completed: {}/{} successful embeddings", embeddings.size(), codeChunks.size());

                // Step 3: Store in vector database
                logger.info("Phase 4: Storing {} embeddings in vector database for repository: {}", embeddings.size(), repositoryUrl);
                progress.setStatus(AnalysisStatus.STORING_VECTORS);
                progress.setMessage("Storing embeddings in vector database");
                boolean stored = vectorStoreService.storeEmbeddings(embeddings).join();

                if (!stored) {
                    logger.error("Failed to store embeddings in vector database for repository: {}", repositoryUrl);
                    throw new RuntimeException("Failed to store embeddings in vector database");
                }
                logger.info("Vector storage completed successfully for repository: {}", repositoryUrl);

                // Complete
                progress.setStatus(AnalysisStatus.COMPLETED);
                progress.setEndTime(System.currentTimeMillis());
                progress.setMessage("Analysis completed successfully");

                // Create result
                AnalysisResult result = new AnalysisResult();
                result.setRepositoryUrl(repositoryUrl);
                result.setStatus(AnalysisStatus.COMPLETED);
                result.setTotalChunks(codeChunks.size());
                result.setStoredEmbeddings(embeddings.size());
                result.setProcessingTimeMs(progress.getDurationMs());
                result.setChunksByType(calculateChunksByType(codeChunks));

                logger.info("✅ Repository analysis completed successfully: {} ({} chunks → {} embeddings) in {}ms",
                        repositoryUrl, result.getTotalChunks(), result.getStoredEmbeddings(), result.getProcessingTimeMs());

                return result;

            } catch (Exception e) {
                progress.setStatus(AnalysisStatus.FAILED);
                progress.setError(e.getMessage());
                progress.setEndTime(System.currentTimeMillis());

                logger.error("❌ Repository analysis failed for {} after {}ms: {}", 
                        repositoryUrl, progress.getDurationMs(), e.getMessage(), e);

                AnalysisResult result = new AnalysisResult();
                result.setRepositoryUrl(repositoryUrl);
                result.setStatus(AnalysisStatus.FAILED);
                result.getErrors().add(e.getMessage());
                result.setProcessingTimeMs(progress.getDurationMs());

                return result;
            }
        });
    }

    /**
     * Get analysis progress for a repository
     */
    public AnalysisProgress getAnalysisProgress(String repositoryUrl) {
        String repoId = generateRepositoryId(repositoryUrl);
        AnalysisProgress progress = analysisProgressMap.get(repoId);
        logger.debug("Progress requested for repository {}: {}", repositoryUrl, 
                progress != null ? progress.getStatus() : "NOT_FOUND");
        return progress;
    }

    /**
     * Analyze specific Java files only
     */
    public CompletableFuture<AnalysisResult> analyzeSpecificFiles(String repositoryUrl, List<String> filePaths) {
        logger.info("Starting specific file analysis for repository: {} ({} files)", repositoryUrl, filePaths.size());
        return CompletableFuture.supplyAsync(() -> {
            try {
                RepositoryService.RepositoryStatus repoStatus = repositoryService.getRepositoryStatus(repositoryUrl);
                if (repoStatus == null || repoStatus.getLocalPath() == null) {
                    logger.error("Repository not found for specific file analysis: {}", repositoryUrl);
                    throw new RuntimeException("Repository not found: " + repositoryUrl);
                }
                logger.debug("Repository found at: {}", repoStatus.getLocalPath());

                // Parse only specific files
                logger.debug("Parsing {} specific files", filePaths.size());
                List<CodeParserService.CodeChunk> codeChunks = new ArrayList<>();
                int parsedFiles = 0;
                for (String filePath : filePaths) {
                    try {
                        java.nio.file.Path fullPath = java.nio.file.Path.of(repoStatus.getLocalPath(), filePath);
                        List<CodeParserService.CodeChunk> fileChunks = codeParserService.parseJavaFile(fullPath, repoStatus.getLocalPath());
                        codeChunks.addAll(fileChunks);
                        parsedFiles++;
                        logger.debug("Successfully parsed file {}: {} chunks", filePath, fileChunks.size());
                    } catch (Exception e) {
                        logger.warn("Failed to parse file {}: {}", filePath, e.getMessage());
                    }
                }
                logger.info("File parsing completed: {}/{} files parsed, {} total chunks", parsedFiles, filePaths.size(), codeChunks.size());

                if (codeChunks.isEmpty()) {
                    logger.error("No code chunks extracted from specified files for repository: {}", repositoryUrl);
                    throw new RuntimeException("No code chunks extracted from specified files");
                }

                // Generate and store embeddings
                logger.info("Generating embeddings for {} chunks from specific files", codeChunks.size());
                List<EmbeddingService.CodeEmbedding> embeddings = embeddingService.generateEmbeddings(codeChunks).join();
                logger.info("Generated {} embeddings, storing in vector database", embeddings.size());
                vectorStoreService.storeEmbeddings(embeddings).join();
                logger.info("Specific file analysis embedding storage completed");

                AnalysisResult result = new AnalysisResult();
                result.setRepositoryUrl(repositoryUrl);
                result.setStatus(AnalysisStatus.COMPLETED);
                result.setTotalChunks(codeChunks.size());
                result.setStoredEmbeddings(embeddings.size());
                result.setChunksByType(calculateChunksByType(codeChunks));

                logger.info("✅ Specific file analysis completed: {} files → {} chunks → {} embeddings", 
                        filePaths.size(), result.getTotalChunks(), result.getStoredEmbeddings());
                return result;

            } catch (Exception e) {
                logger.error("❌ Specific file analysis failed for repository {}: {}", repositoryUrl, e.getMessage(), e);

                AnalysisResult result = new AnalysisResult();
                result.setRepositoryUrl(repositoryUrl);
                result.setStatus(AnalysisStatus.FAILED);
                result.getErrors().add(e.getMessage());

                return result;
            }
        });
    }

    /**
     * Get analysis summary for all processed repositories
     */
    public Map<String, Object> getAnalysisSummary() {
        logger.debug("Generating analysis summary");
        Map<String, Object> summary = new HashMap<>();

        // Get vector store stats
        try {
            logger.debug("Retrieving vector store statistics");
            Map<String, Object> collectionStats = vectorStoreService.getCollectionStats().join();
            summary.put("vectorStore", collectionStats);
            logger.debug("Vector store stats retrieved successfully");
        } catch (Exception e) {
            logger.warn("Failed to get vector store stats: {}", e.getMessage());
        }

        // Get analysis progress for all repositories
        Map<String, AnalysisProgress> allProgress = new HashMap<>(analysisProgressMap);
        summary.put("repositories", allProgress);

        // Calculate totals
        int totalRepositories = allProgress.size();
        long completedRepositories = allProgress.values().stream()
                .mapToLong(p -> p.getStatus() == AnalysisStatus.COMPLETED ? 1 : 0)
                .sum();
        long failedRepositories = allProgress.values().stream()
                .mapToLong(p -> p.getStatus() == AnalysisStatus.FAILED ? 1 : 0)
                .sum();

        summary.put("totalRepositories", totalRepositories);
        summary.put("completedRepositories", completedRepositories);
        summary.put("failedRepositories", failedRepositories);
        summary.put("inProgressRepositories", totalRepositories - completedRepositories - failedRepositories);

        logger.info("Analysis summary generated: {} total repos ({} completed, {} failed, {} in progress)", 
                totalRepositories, completedRepositories, failedRepositories, totalRepositories - completedRepositories - failedRepositories);
        return summary;
    }

    /**
     * Check if services are healthy
     */
    public CompletableFuture<Map<String, Boolean>> healthCheck() {
        logger.debug("Performing comprehensive health check of analysis services");
        CompletableFuture<Boolean> embeddingHealth = embeddingService.healthCheck();
        CompletableFuture<Boolean> vectorStoreHealth = vectorStoreService.healthCheck();

        return CompletableFuture.allOf(embeddingHealth, vectorStoreHealth)
                .thenApply(v -> {
                    boolean embeddingHealthy = embeddingHealth.join();
                    boolean vectorStoreHealthy = vectorStoreHealth.join();
                    boolean overallHealthy = embeddingHealthy && vectorStoreHealthy;
                    
                    Map<String, Boolean> health = new HashMap<>();
                    health.put("embeddingService", embeddingHealthy);
                    health.put("vectorStore", vectorStoreHealthy);
                    health.put("overall", overallHealthy);
                    
                    logger.info("Health check completed - Embedding: {}, VectorStore: {}, Overall: {}", 
                            embeddingHealthy, vectorStoreHealthy, overallHealthy);
                    return health;
                });
    }

    // Helper methods
    private Map<String, Integer> calculateChunksByType(List<CodeParserService.CodeChunk> chunks) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("CLASS", 0);
        counts.put("METHOD", 0);
        counts.put("INTERFACE", 0);

        for (CodeParserService.CodeChunk chunk : chunks) {
            counts.merge(chunk.getType(), 1, Integer::sum);
        }

        logger.trace("Chunk type distribution: {}", counts);
        return counts;
    }

    private String generateRepositoryId(String repositoryUrl) {
        return String.valueOf(repositoryUrl.hashCode());
    }
}