package com.suhas.codeAnalyzer.controller;

import com.suhas.codeAnalyzer.model.UserRepository;
import com.suhas.codeAnalyzer.service.UserSessionService;
import com.suhas.codeAnalyzer.service.UserSessionService.SessionInitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*") // Configure properly for production
public class UserSessionController {
    
    private static final Logger log = LoggerFactory.getLogger(UserSessionController.class);
    
    @Autowired
    private UserSessionService userSessionService;
    
    /**
     * Initialize or resume user session
     */
    @PostMapping("/session/start")
    public ResponseEntity<Map<String, Object>> startSession(@RequestBody SessionStartRequest request) {
        try {
            log.info("Starting session for email: {}", request.getEmail());
            
            SessionInitResult result = userSessionService.initializeUserSession(request.getEmail());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", Map.of(
                "email", result.getUser().getEmail(),
                "isNew", result.isNewUser(),
                "lastVisit", result.getUser().getLastVisit()
            ));
            response.put("session", Map.of(
                "id", result.getSession().getId(),
                "currentRepository", result.getSession().getCurrentRepository(),
                "lastActivity", result.getSession().getLastActivity()
            ));
            response.put("repositories", result.getRepositories().stream()
                .map(repo -> Map.of(
                    "url", repo.getUrl(),
                    "name", repo.getName(),
                    "owner", repo.getOwner(),
                    "status", repo.getStatus().toString(),
                    "analyzedAt", repo.getAnalyzedAt(),
                    "fileCount", repo.getFileCount()
                )).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error starting session for email: {}", request.getEmail(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to initialize session");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Get user's repositories
     */
    @GetMapping("/{email}/repositories")
    public ResponseEntity<Map<String, Object>> getUserRepositories(@PathVariable @Email String email) {
        try {
            log.info("Getting repositories for user: {}", email);
            
            List<UserRepository> repositories = userSessionService.getUserRepositories(email);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("email", email);
            response.put("repositories", repositories.stream()
                .map(repo -> Map.of(
                    "url", repo.getUrl(),
                    "name", repo.getName(),
                    "owner", repo.getOwner(),
                    "status", repo.getStatus().toString(),
                    "analyzedAt", repo.getAnalyzedAt(),
                    "createdAt", repo.getCreatedAt(),
                    "fileCount", repo.getFileCount() != null ? repo.getFileCount() : 0
                )).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting repositories for user: {}", email, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to get repositories",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Continue session with specific repository
     */
    @PostMapping("/session/continue")
    public ResponseEntity<Map<String, Object>> continueSession(@RequestBody ContinueSessionRequest request) {
        try {
            log.info("Continuing session for user: {} with repository: {}", 
                    request.getEmail(), request.getRepositoryUrl());
            
            // Check if user has analyzed this repository
            boolean hasAnalyzed = userSessionService.hasUserAnalyzedRepository(
                    request.getEmail(), request.getRepositoryUrl());
            
            if (!hasAnalyzed) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Repository not found or not analyzed",
                    "message", "Please analyze this repository first"
                ));
            }
            
            // Extract repository ID from URL for session context
            String repositoryId = generateRepositoryId(request.getRepositoryUrl());
            
            // Update session context
            userSessionService.updateSessionRepository(
                    request.getEmail(), 
                    request.getRepositoryUrl(), 
                    repositoryId
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Session continued with repository: " + request.getRepositoryUrl());
            response.put("repositoryUrl", request.getRepositoryUrl());
            response.put("repositoryId", repositoryId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error continuing session for user: {}", request.getEmail(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to continue session",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Get current session info
     */
    @GetMapping("/{email}/session")
    public ResponseEntity<Map<String, Object>> getCurrentSession(@PathVariable @Email String email) {
        try {
            var sessionOpt = userSessionService.getCurrentSession(email);
            
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasSession", false,
                    "message", "No active session found"
                ));
            }
            
            var session = sessionOpt.get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasSession", true);
            response.put("session", Map.of(
                "id", session.getId(),
                "currentRepository", session.getCurrentRepository(),
                "currentRepositoryId", session.getCurrentRepositoryId(),
                "lastActivity", session.getLastActivity(),
                "exchangeCount", session.getConversationHistory().size()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting session for user: {}", email, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to get session",
                "message", e.getMessage()
            ));
        }
    }
    
    // Helper method to generate repository ID from URL
    private String generateRepositoryId(String repoUrl) {
        if (repoUrl == null) return "unknown_repo";

        try {
            // Extract owner/repo from GitHub URL
            String[] parts = repoUrl.replace("https://github.com/", "")
                    .replace("http://github.com/", "")
                    .replace(".git", "")
                    .split("/");
            if (parts.length >= 2) {
                return parts[0] + "_" + parts[1];
            }
        } catch (Exception e) {
            log.warn("Failed to generate repository ID from URL: {}", repoUrl);
        }

        // Ultimate fallback
        return "repo_" + Math.abs(repoUrl.hashCode());
    }
    
    // Request DTOs
    public static class SessionStartRequest {
        @NotBlank
        @Email
        private String email;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
    
    public static class ContinueSessionRequest {
        @NotBlank
        @Email
        private String email;
        
        @NotBlank
        private String repositoryUrl;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    }
}