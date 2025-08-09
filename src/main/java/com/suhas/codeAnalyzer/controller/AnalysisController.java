package com.suhas.codeAnalyzer.controller;

import com.suhas.codeAnalyzer.service.CodeAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /**
     * Start complete analysis of a repository
     * POST /api/analysis/start?url=https://github.com/user/repo.git
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAnalysis(@RequestParam String url) {
        logger.info("Starting analysis for repository: {}", url);

        try {
            // Start analysis asynchronously
            CompletableFuture<CodeAnalysisService.AnalysisResult> analysisFuture =
                    codeAnalysisService.analyzeRepository(url);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Analysis started");
            response.put("repositoryUrl", url);
            response.put("status", "INITIALIZING");

            // Handle completion
            analysisFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Analysis failed for {}: {}", url, throwable.getMessage());
                } else {
                    logger.info("Analysis completed for {}: {} chunks processed",
                            url, result.getTotalChunks());
                }
            });

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Failed to start analysis for {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to start analysis");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("repositoryUrl", url);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get analysis progress
     * GET /api/analysis/progress?url=https://github.com/user/repo.git
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getAnalysisProgress(@RequestParam String url) {
        logger.debug("Getting analysis progress for repository: {}", url);

        try {
            CodeAnalysisService.AnalysisProgress progress = codeAnalysisService.getAnalysisProgress(url);

            Map<String, Object> response = new HashMap<>();
            response.put("repositoryUrl", url);

            if (progress == null) {
                response.put("status", "NOT_STARTED");
                response.put("message", "Analysis not started for this repository");
                return ResponseEntity.ok(response);
            }

            response.put("status", progress.getStatus().toString());
            response.put("message", progress.getMessage());
            response.put("progressPercentage", progress.getProgressPercentage());
            response.put("totalChunks", progress.getTotalChunks());
            response.put("processedChunks", progress.getProcessedChunks());
            response.put("durationMs", progress.getDurationMs());

            if (progress.getError() != null) {
                response.put("error", progress.getError());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting analysis progress for {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get analysis progress");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Analyze specific files only
     * POST /api/analysis/files?url=https://github.com/user/repo.git
     * Body: ["src/main/java/Controller.java", "src/main/java/Service.java"]
     */
    @PostMapping("/files")
    public ResponseEntity<Map<String, Object>> analyzeSpecificFiles(
            @RequestParam String url,
            @RequestBody List<String> filePaths) {

        logger.info("Starting analysis of {} specific files for repository: {}", filePaths.size(), url);

        try {
            CompletableFuture<CodeAnalysisService.AnalysisResult> analysisFuture =
                    codeAnalysisService.analyzeSpecificFiles(url, filePaths);

            CodeAnalysisService.AnalysisResult result = analysisFuture.join();

            Map<String, Object> response = new HashMap<>();
            response.put("repositoryUrl", url);
            response.put("analyzedFiles", filePaths);
            response.put("status", result.getStatus().toString());
            response.put("totalChunks", result.getTotalChunks());
            response.put("storedEmbeddings", result.getStoredEmbeddings());
            response.put("processingTimeMs", result.getProcessingTimeMs());
            response.put("chunksByType", result.getChunksByType());

            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to analyze specific files for {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to analyze specific files");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("repositoryUrl", url);
            errorResponse.put("requestedFiles", filePaths);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get analysis summary for all repositories
     * GET /api/analysis/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getAnalysisSummary() {
        logger.debug("Getting analysis summary");

        try {
            Map<String, Object> summary = codeAnalysisService.getAnalysisSummary();
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            logger.error("Failed to get analysis summary: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get analysis summary");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Health check for analysis services
     * GET /api/analysis/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        logger.debug("Performing health check");

        try {
            Map<String, Boolean> healthStatus = codeAnalysisService.healthCheck().join();

            Map<String, Object> response = new HashMap<>();
            response.put("health", healthStatus);
            response.put("timestamp", System.currentTimeMillis());

            boolean overall = healthStatus.getOrDefault("overall", false);

            if (overall) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response); // Service Unavailable
            }

        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Health check failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(503).body(errorResponse);
        }
    }
}