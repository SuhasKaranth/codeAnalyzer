package com.suhas.codeAnalyzer.controller;

import com.suhas.codeAnalyzer.service.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/repository")
public class RepositoryController {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryController.class);

    @Autowired
    private RepositoryService repositoryService;

    /**
     * Clone a repository (async)
     * POST /api/repository/clone?url=https://github.com/user/repo.git
     */
    @PostMapping("/clone")
    public ResponseEntity<Map<String, Object>> cloneRepository(@RequestParam String url) {
        logger.info("Received request to clone repository: {}", url);

        try {
            // Start async cloning
            CompletableFuture<String> cloneFuture = repositoryService.cloneRepositoryAsync(url);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Repository cloning started");
            response.put("repositoryUrl", url);
            response.put("status", "CLONING");

            // You can add a callback to handle completion if needed
            cloneFuture.whenComplete((localPath, throwable) -> {
                if (throwable != null) {
                    logger.error("Repository cloning failed for {}: {}", url, throwable.getMessage());
                } else {
                    logger.info("Repository cloning completed for {}: {}", url, localPath);
                }
            });

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Error starting repository clone for {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to start repository cloning");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("repositoryUrl", url);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Clone a repository (sync) - for testing
     * POST /api/repository/clone-sync?url=https://github.com/user/repo.git
     */
    @PostMapping("/clone-sync")
    public ResponseEntity<Map<String, Object>> cloneRepositorySync(@RequestParam String url) {
        logger.info("Received request to clone repository synchronously: {}", url);

        try {
            String localPath = repositoryService.cloneRepository(url);

            // Get repository statistics
            RepositoryService.RepositoryStats stats = repositoryService.getRepositoryStats(localPath);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Repository cloned successfully");
            response.put("repositoryUrl", url);
            response.put("localPath", localPath);
            response.put("status", "COMPLETED");
            response.put("stats", Map.of(
                    "javaFileCount", stats.getJavaFileCount(),
                    "totalLines", stats.getTotalLines()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error cloning repository {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to clone repository");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("repositoryUrl", url);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get repository cloning status
     * GET /api/repository/status?url=https://github.com/user/repo.git
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRepositoryStatus(@RequestParam String url) {
        logger.info("Checking status for repository: {}", url);

        try {
            RepositoryService.RepositoryStatus status = repositoryService.getRepositoryStatus(url);

            Map<String, Object> response = new HashMap<>();

            if (status == null) {
                response.put("message", "Repository not found or not processed");
                response.put("repositoryUrl", url);
                response.put("status", "NOT_FOUND");
                return ResponseEntity.notFound().build();
            }

            response.put("repositoryUrl", url);
            response.put("status", status.getStatus().toString());
            response.put("message", status.getMessage());
            response.put("timestamp", status.getTimestamp());

            if (status.getLocalPath() != null) {
                response.put("localPath", status.getLocalPath());

                // Add stats if completed
                if (status.getStatus() == RepositoryService.ProcessingStatus.COMPLETED) {
                    try {
                        RepositoryService.RepositoryStats stats = repositoryService.getRepositoryStats(status.getLocalPath());
                        response.put("stats", Map.of(
                                "javaFileCount", stats.getJavaFileCount(),
                                "totalLines", stats.getTotalLines()
                        ));
                    } catch (Exception e) {
                        logger.warn("Could not get stats for repository {}: {}", url, e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting repository status for {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get repository status");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("repositoryUrl", url);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * List Java files in a repository
     * GET /api/repository/files?url=https://github.com/user/repo.git
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listJavaFiles(@RequestParam String url) {
        logger.info("Listing Java files for repository: {}", url);

        try {
            RepositoryService.RepositoryStatus status = repositoryService.getRepositoryStatus(url);

            if (status == null || status.getLocalPath() == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Repository not cloned yet");
                errorResponse.put("message", "Please clone the repository first");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            java.util.List<java.nio.file.Path> javaFiles = repositoryService.listJavaFiles(status.getLocalPath());

            // Convert paths to relative strings for better readability
            java.util.List<String> relativePaths = javaFiles.stream()
                    .map(path -> {
                        String localPath = status.getLocalPath();
                        String fullPath = path.toString();
                        return fullPath.substring(localPath.length() + 1); // +1 for the separator
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("repositoryUrl", url);
            response.put("localPath", status.getLocalPath());
            response.put("javaFiles", relativePaths);
            response.put("fileCount", relativePaths.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error listing Java files for repository {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to list Java files");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Clean up repository
     * DELETE /api/repository/cleanup?url=https://github.com/user/repo.git
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupRepository(@RequestParam String url) {
        logger.info("Cleaning up repository: {}", url);

        try {
            RepositoryService.RepositoryStatus status = repositoryService.getRepositoryStatus(url);

            if (status == null || status.getLocalPath() == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Repository not found locally");
                response.put("repositoryUrl", url);
                return ResponseEntity.ok(response);
            }

            boolean cleaned = repositoryService.cleanupRepository(status.getLocalPath());

            Map<String, Object> response = new HashMap<>();
            response.put("repositoryUrl", url);
            response.put("cleaned", cleaned);
            response.put("message", cleaned ? "Repository cleaned up successfully" : "Repository cleanup failed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error cleaning up repository {}: {}", url, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to cleanup repository");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
