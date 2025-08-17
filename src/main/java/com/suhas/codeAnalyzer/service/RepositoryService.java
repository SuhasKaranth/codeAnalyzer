package com.suhas.codeAnalyzer.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class RepositoryService {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryService.class);

    @Value("${app.workspace.directory:./workspace}")
    private String workspaceDirectory;

    // Track repository processing status
    private final Map<String, RepositoryStatus> repositoryStatuses = new ConcurrentHashMap<>();

    public enum ProcessingStatus {
        CLONING, COMPLETED, FAILED, ANALYZING
    }

    public static class RepositoryStatus {
        private ProcessingStatus status;
        private String message;
        private String localPath;
        private long timestamp;

        public RepositoryStatus(ProcessingStatus status, String message) {
            this.status = status;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and setters
        public ProcessingStatus getStatus() { return status; }
        public void setStatus(ProcessingStatus status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Clone repository asynchronously
     */
    public CompletableFuture<String> cloneRepositoryAsync(String repoUrl) {
        String repoId = generateRepositoryId(repoUrl);
        logger.info("Starting async clone for repository: {}", repoUrl);

        // Update status to cloning
        repositoryStatuses.put(repoId, new RepositoryStatus(ProcessingStatus.CLONING, "Starting clone process"));
        logger.debug("Repository status updated to CLONING for: {}", repoUrl);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String localPath = cloneRepository(repoUrl);

                RepositoryStatus status = repositoryStatuses.get(repoId);
                status.setStatus(ProcessingStatus.COMPLETED);
                status.setMessage("Repository cloned successfully");
                status.setLocalPath(localPath);

                logger.info("Async repository clone completed successfully: {} -> {}", repoUrl, localPath);
                return localPath;

            } catch (Exception e) {
                RepositoryStatus status = repositoryStatuses.get(repoId);
                status.setStatus(ProcessingStatus.FAILED);
                status.setMessage("Clone failed: " + e.getMessage());

                logger.error("Failed to clone repository: {}", repoUrl, e);
                throw new RuntimeException("Failed to clone repository: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Clone repository synchronously
     */
    public String cloneRepository(String repoUrl) throws GitAPIException, IOException {
        logger.info("Starting synchronous clone for repository: {}", repoUrl);
        validateRepositoryUrl(repoUrl);
        logger.debug("Repository URL validation passed for: {}", repoUrl);

        // Create workspace directory if it doesn't exist
        Path workspacePath = Paths.get(workspaceDirectory);
        if (!Files.exists(workspacePath)) {
            Files.createDirectories(workspacePath);
            logger.info("Created workspace directory: {}", workspacePath.toAbsolutePath());
        }

        // Generate unique directory name for this repository
        String repoName = extractRepositoryName(repoUrl);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String targetDirName = repoName + "_" + timestamp;
        Path targetPath = workspacePath.resolve(targetDirName);

        // Clean up if directory already exists
        if (Files.exists(targetPath)) {
            deleteDirectory(targetPath);
        }

        logger.info("Cloning repository {} to {}", repoUrl, targetPath.toAbsolutePath());
        logger.debug("Generated unique directory name: {}", targetDirName);

        try {
            // Clone the repository
            logger.debug("Initiating Git clone operation for: {}", repoUrl);
            Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(targetPath.toFile())
                    .setCloneAllBranches(false) // Only clone default branch for POC
                    .call();

            git.close();
            logger.debug("Git repository clone completed, connection closed for: {}", repoUrl);

            // Add this line in cloneRepository method, just before the return statement
            RepositoryStatus status = new RepositoryStatus(ProcessingStatus.COMPLETED, "Repository cloned successfully");
            status.setLocalPath(targetPath.toAbsolutePath().toString());
            repositoryStatuses.put(generateRepositoryId(repoUrl), status);

            logger.info("Successfully cloned repository to: {}", targetPath.toAbsolutePath());
            return targetPath.toAbsolutePath().toString();

        } catch (GitAPIException e) {
            logger.error("Git clone operation failed for repository: {}", repoUrl, e);
            // Clean up on failure
            if (Files.exists(targetPath)) {
                logger.debug("Cleaning up failed clone directory: {}", targetPath);
                deleteDirectory(targetPath);
            }
            throw e;
        }
    }

    /**
     * Get repository processing status
     */
    public RepositoryStatus getRepositoryStatus(String repoUrl) {
        String repoId = generateRepositoryId(repoUrl);
        return repositoryStatuses.get(repoId);
    }

    /**
     * List all Java files in the cloned repository
     */
    public java.util.List<Path> listJavaFiles(String localRepoPath) throws IOException {
        logger.debug("Scanning for Java files in repository: {}", localRepoPath);
        Path repoPath = Paths.get(localRepoPath);

        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + localRepoPath);
        }


        logger.info("Found {} Java files in repository: {}", Files.walk(repoPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/.git/"))
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/build/"))
                .count(), localRepoPath);

        return Files.walk(repoPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/.git/"))
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/build/"))
                .sorted()
                .toList();
    }

    /**
     * Clean up repository directory
     */
    public boolean cleanupRepository(String localRepoPath) {
        try {
            Path repoPath = Paths.get(localRepoPath);
            if (Files.exists(repoPath)) {
                deleteDirectory(repoPath);
                logger.info("Cleaned up repository directory: {}", localRepoPath);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.error("Failed to cleanup repository directory: {}", localRepoPath, e);
            return false;
        }
    }

    /**
     * Get repository statistics
     */
    public RepositoryStats getRepositoryStats(String localRepoPath) throws IOException {
        logger.debug("Calculating repository statistics for: {}", localRepoPath);
        java.util.List<Path> javaFiles = listJavaFiles(localRepoPath);

        long totalLines = 0;
        long totalFiles = javaFiles.size();

        for (Path javaFile : javaFiles) {
            totalLines += Files.lines(javaFile).count();
        }

        logger.info("Repository statistics calculated - Files: {}, Lines: {}, Path: {}", totalFiles, totalLines, localRepoPath);
        return new RepositoryStats(totalFiles, totalLines, localRepoPath);
    }

    public static class RepositoryStats {
        private long javaFileCount;
        private long totalLines;
        private String localPath;

        public RepositoryStats(long javaFileCount, long totalLines, String localPath) {
            this.javaFileCount = javaFileCount;
            this.totalLines = totalLines;
            this.localPath = localPath;
        }

        // Getters
        public long getJavaFileCount() { return javaFileCount; }
        public long getTotalLines() { return totalLines; }
        public String getLocalPath() { return localPath; }
    }

    // Helper methods
    private void validateRepositoryUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository URL cannot be null or empty");
        }

        if (!repoUrl.startsWith("https://") && !repoUrl.startsWith("git://")) {
            throw new IllegalArgumentException("Only HTTPS and Git protocols are supported");
        }
    }

    private String extractRepositoryName(String repoUrl) {
        // Extract repository name from URL
        // e.g., https://github.com/user/repo.git -> repo
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1].replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String generateRepositoryId(String repoUrl) {
        return String.valueOf(repoUrl.hashCode());
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}