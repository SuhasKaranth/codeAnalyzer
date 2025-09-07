package com.suhas.codeAnalyzer.service;

import com.suhas.codeAnalyzer.model.UserSession;
import com.suhas.codeAnalyzer.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserSessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSessionService.class);
    
    @Autowired
    private UserSessionRepository sessionRepository;
    
    public UserSession createOrUpdateSession(String sessionId, String email) {
        try {
            Optional<UserSession> existingSession = sessionRepository.findBySessionId(sessionId);
            
            if (existingSession.isPresent()) {
                UserSession session = existingSession.get();
                session.updateLastAccessed();
                session.setActive(true);
                session.setEmail(email); // Update email if changed
                
                UserSession saved = sessionRepository.save(session);
                logger.info("Updated existing session: {} for email: {}", sessionId, email);
                return saved;
            } else {
                UserSession newSession = new UserSession(sessionId, email);
                UserSession saved = sessionRepository.save(newSession);
                logger.info("Created new session: {} for email: {}", sessionId, email);
                return saved;
            }
        } catch (DuplicateKeyException e) {
            logger.warn("Duplicate session ID detected: {}. Fetching existing session.", sessionId);
            return sessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session creation failed: " + sessionId));
        }
    }
    
    public Optional<UserSession> getSessionById(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }
    
    public boolean validateSession(String sessionId) {
        Optional<UserSession> session = sessionRepository.findBySessionId(sessionId);
        if (session.isPresent() && session.get().isActive()) {
            // Update last accessed time
            UserSession userSession = session.get();
            userSession.updateLastAccessed();
            sessionRepository.save(userSession);
            return true;
        }
        return false;
    }
    
    public List<UserSession> getUserActiveSessions(String email) {
        return sessionRepository.findByEmailAndActiveTrue(email);
    }
    
    public List<UserSession> getAllActiveSessions() {
        return sessionRepository.findByActiveTrue();
    }
    
    public void deactivateSession(String sessionId) {
        Optional<UserSession> session = sessionRepository.findBySessionId(sessionId);
        if (session.isPresent()) {
            UserSession userSession = session.get();
            userSession.setActive(false);
            sessionRepository.save(userSession);
            logger.info("Deactivated session: {}", sessionId);
        }
    }
    
    public void deleteSession(String sessionId) {
        sessionRepository.deleteBySessionId(sessionId);
        logger.info("Deleted session: {}", sessionId);
    }
    
    public void cleanupExpiredSessions(int hoursOld) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hoursOld);
        List<UserSession> expiredSessions = sessionRepository.findByLastAccessedAtBefore(cutoffTime);
        
        for (UserSession session : expiredSessions) {
            session.setActive(false);
            sessionRepository.save(session);
        }
        
        logger.info("Cleaned up {} expired sessions older than {} hours", expiredSessions.size(), hoursOld);
    }
    
    public int getActiveSessionCount() {
        return sessionRepository.findByActiveTrue().size();
    }
    
    public int getUserActiveSessionCount(String email) {
        return sessionRepository.findByEmailAndActiveTrue(email).size();
    }
}