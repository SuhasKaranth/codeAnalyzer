package com.suhas.codeAnalyzer.controller;

import com.suhas.codeAnalyzer.model.UserSession;
import com.suhas.codeAnalyzer.service.UserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/user")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000"})
public class UserSessionController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSessionController.class);
    
    @Autowired
    private UserSessionService sessionService;
    
    @PostMapping("/session/start")
    public ResponseEntity<Map<String, Object>> startSession(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String email = request.get("email");
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createResponse(false, "Session ID is required", null));
            }
            
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createResponse(false, "Email is required", null));
            }
            
            UserSession session = sessionService.createOrUpdateSession(sessionId, email);
            
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", session.getSessionId());
            sessionData.put("email", session.getEmail());
            sessionData.put("createdAt", session.getCreatedAt());
            sessionData.put("active", session.isActive());
            
            logger.info("Session started successfully: {}", sessionId);
            return ResponseEntity.ok(createResponse(true, "Session started successfully", sessionData));
            
        } catch (Exception e) {
            logger.error("Error starting session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to start session: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/session/continue")
    public ResponseEntity<Map<String, Object>> continueSession(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createResponse(false, "Session ID is required", null));
            }
            
            boolean isValid = sessionService.validateSession(sessionId);
            
            if (isValid) {
                Optional<UserSession> session = sessionService.getSessionById(sessionId);
                if (session.isPresent()) {
                    Map<String, Object> sessionData = new HashMap<>();
                    sessionData.put("sessionId", session.get().getSessionId());
                    sessionData.put("email", session.get().getEmail());
                    sessionData.put("lastAccessedAt", session.get().getLastAccessedAt());
                    sessionData.put("active", session.get().isActive());
                    
                    return ResponseEntity.ok(createResponse(true, "Session is valid", sessionData));
                }
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createResponse(false, "Invalid or expired session", null));
            
        } catch (Exception e) {
            logger.error("Error continuing session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to continue session: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        try {
            Optional<UserSession> session = sessionService.getSessionById(sessionId);
            
            if (session.isPresent()) {
                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("sessionId", session.get().getSessionId());
                sessionData.put("email", session.get().getEmail());
                sessionData.put("createdAt", session.get().getCreatedAt());
                sessionData.put("lastAccessedAt", session.get().getLastAccessedAt());
                sessionData.put("active", session.get().isActive());
                
                return ResponseEntity.ok(createResponse(true, "Session found", sessionData));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createResponse(false, "Session not found", null));
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to retrieve session: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/sessions/{email}")
    public ResponseEntity<Map<String, Object>> getUserSessions(@PathVariable String email) {
        try {
            List<UserSession> sessions = sessionService.getUserActiveSessions(email);
            
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("sessionCount", sessions.size());
            data.put("sessions", sessions);
            
            return ResponseEntity.ok(createResponse(true, "User sessions retrieved", data));
            
        } catch (Exception e) {
            logger.error("Error retrieving user sessions for: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to retrieve user sessions: " + e.getMessage(), null));
        }
    }
    
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> endSession(@PathVariable String sessionId) {
        try {
            sessionService.deactivateSession(sessionId);
            return ResponseEntity.ok(createResponse(true, "Session ended successfully", null));
            
        } catch (Exception e) {
            logger.error("Error ending session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to end session: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/sessions/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        try {
            int totalActive = sessionService.getActiveSessionCount();
            List<UserSession> allSessions = sessionService.getAllActiveSessions();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalActiveSessions", totalActive);
            stats.put("activeSessions", allSessions);
            
            return ResponseEntity.ok(createResponse(true, "Session statistics retrieved", stats));
            
        } catch (Exception e) {
            logger.error("Error retrieving session stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Failed to retrieve session stats: " + e.getMessage(), null));
        }
    }
    
    private Map<String, Object> createResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }
}