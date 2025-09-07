package com.suhas.codeAnalyzer.repository;

import com.suhas.codeAnalyzer.model.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends MongoRepository<UserSession, String> {
    
    Optional<UserSession> findBySessionId(String sessionId);
    
    List<UserSession> findByEmailAndActiveTrue(String email);
    
    List<UserSession> findByActiveTrue();
    
    List<UserSession> findByLastAccessedAtBefore(LocalDateTime cutoffTime);
    
    void deleteBySessionId(String sessionId);
    
    void deleteByEmail(String email);
}