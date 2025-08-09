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

        logger.info("Starting complete analysis for repository: {}", repositoryUrl);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure vector store is ready
                progress.setStatus(AnalysisStatus.INITIALIZING);
                progress.setMessage("Initializing vector store");
                vectorStoreService.initializeCollection().join();

                // Get repository status (should already be cloned)
                RepositoryService.RepositoryStatus repoStatus = repositoryService.getRepositoryStatus(repositoryUrl);
                if (repoStatus == null || repoStatus.getLocalPath() == null) {
                    throw new RuntimeException("Repository not found or not cloned: " + repositoryUrl);
                }

                // Step 1: Parse repository
                progress.setStatus(AnalysisStatus.PARSING);
                progress.setMessage("Parsing Java files");
                List<CodeParserService.CodeChunk> codeChunks = codeParserService.parseRepository(repoStatus.getLocalPath());

                progress.setTotalChunks(codeChunks.size());
                progress.setMessage(String.format("Found %d code chunks", codeChunks.size()));

                if (codeChunks.isEmpty()) {
                    throw new RuntimeException("No Java code chunks found in repository");
                }

                // Step 2: Generate embeddings
                progress.setStatus(AnalysisStatus.GENERATING_EMBEDDINGS);
                progress.setMessage("Generating embeddings");
                List<EmbeddingService.CodeEmbedding> embeddings = embeddingService.generateEmbeddings(codeChunks).join();

                progress.setProcessedChunks(embeddings.size());
                progress.setMessage(String.format("Generated %d embeddings", embeddings.size()));

                // Step 3: Store in vector database
                progress.setStatus(AnalysisStatus.STORING_VECTORS);
                progress.setMessage("Storing embeddings in vector database");
                boolean stored = vectorStoreService.storeEmbeddings(embeddings).join();

                if (!stored) {
                    throw new RuntimeException("Failed to store embeddings in vector database");
                }

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

                logger.info("Successfully completed analysis for repository: {} in {}ms",
                        repositoryUrl, result.getProcessingTimeMs());

                return result;

            } catch (Exception e) {
                progress.setStatus(AnalysisStatus.FAILED);
                progress.setError(e.getMessage());
                progress.setEndTime(System.currentTimeMillis());

                logger.error("Failed to analyze repository {}: {}", repositoryUrl, e.getMessage(), e);

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
        return analysisProgressMap.get(repoId);
    }

    /**
     * Analyze specific Java files only
     */
    public CompletableFuture<AnalysisResult> analyzeSpecificFiles(String repositoryUrl, List<String> filePaths) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RepositoryService.RepositoryStatus repoStatus = repositoryService.getRepositoryStatus(repositoryUrl);
                if (repoStatus == null || repoStatus.getLocalPath() == null) {
                    throw new RuntimeException("Repository not found: " + repositoryUrl);
                }

                // Parse only specific files
                List<CodeParserService.CodeChunk> codeChunks = new ArrayList<>();
                for (String filePath : filePaths) {
                    try {
                        java.nio.file.Path fullPath = java.nio.file.Path.of(repoStatus.getLocalPath(), filePath);
                        List<CodeParserService.CodeChunk> fileChunks = codeParserService.parseJavaFile(fullPath, repoStatus.getLocalPath());
                        codeChunks.addAll(fileChunks);
                    } catch (Exception e) {
                        logger.warn("Failed to parse file {}: {}", filePath, e.getMessage());
                    }
                }

                if (codeChunks.isEmpty()) {
                    throw new RuntimeException("No code chunks extracted from specified files");
                }

                // Generate and store embeddings
                List<EmbeddingService.CodeEmbedding> embeddings = embeddingService.generateEmbeddings(codeChunks).join();
                vectorStoreService.storeEmbeddings(embeddings).join();

                AnalysisResult result = new AnalysisResult();
                result.setRepositoryUrl(repositoryUrl);
                result.setStatus(AnalysisStatus.COMPLETED);
                result.setTotalChunks(codeChunks.size());
                result.setStoredEmbeddings(embeddings.size());
                result.setChunksByType(calculateChunksByType(codeChunks));

                return result;

            } catch (Exception e) {
                logger.error("Failed to analyze specific files for {}: {}", repositoryUrl, e.getMessage());

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
        Map<String, Object> summary = new HashMap<>();

        // Get vector store stats
        try {
            Map<String, Object> collectionStats = vectorStoreService.getCollectionStats().join();
            summary.put("vectorStore", collectionStats);
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

        return summary;
    }

    /**
     * Check if services are healthy
     */
    public CompletableFuture<Map<String, Boolean>> healthCheck() {
        CompletableFuture<Boolean> embeddingHealth = embeddingService.healthCheck();
        CompletableFuture<Boolean> vectorStoreHealth = vectorStoreService.healthCheck();

        return CompletableFuture.allOf(embeddingHealth, vectorStoreHealth)
                .thenApply(v -> {
                    Map<String, Boolean> health = new HashMap<>();
                    health.put("embeddingService", embeddingHealth.join());
                    health.put("vectorStore", vectorStoreHealth.join());
                    health.put("overall", embeddingHealth.join() && vectorStoreHealth.join());
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

        return counts;
    }

    private String generateRepositoryId(String repositoryUrl) {
        return String.valueOf(repositoryUrl.hashCode());
    }
}