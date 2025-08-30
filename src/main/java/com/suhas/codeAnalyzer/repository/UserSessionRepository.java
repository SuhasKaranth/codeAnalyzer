package com.suhas.codeAnalyzer.repository;

import com.suhas.codeAnalyzer.model.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends MongoRepository<UserSession, String> {
    
    /**
     * Find active session for a user (most recent)
     */
    Optional<UserSession> findTopByUserEmailOrderByLastActivityDesc(String userEmail);
    
    /**
     * Find all sessions for a user, ordered by last activity
     */
    List<UserSession> findByUserEmailOrderByLastActivityDesc(String userEmail);
    
    /**
     * Find sessions by user and repository
     */
    List<UserSession> findByUserEmailAndCurrentRepository(String userEmail, String currentRepository);
    
    /**
     * Delete sessions older than specified date
     */
    void deleteByLastActivityBefore(LocalDateTime cutoffDate);
    
    /**
     * Count sessions for a user
     */
    long countByUserEmail(String userEmail);
}