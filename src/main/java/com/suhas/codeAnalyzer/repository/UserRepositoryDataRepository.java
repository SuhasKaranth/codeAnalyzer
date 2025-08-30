package com.suhas.codeAnalyzer.repository;

import com.suhas.codeAnalyzer.model.UserRepository;
import com.suhas.codeAnalyzer.model.UserRepository.RepositoryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepositoryDataRepository extends MongoRepository<UserRepository, String> {
    
    /**
     * Find all repositories for a user
     */
    List<UserRepository> findByUserEmailOrderByAnalyzedAtDesc(String userEmail);
    
    /**
     * Find repositories by user and status
     */
    List<UserRepository> findByUserEmailAndStatus(String userEmail, RepositoryStatus status);
    
    /**
     * Find specific repository for a user
     */
    Optional<UserRepository> findByUserEmailAndUrl(String userEmail, String url);
    
    /**
     * Find completed repositories for a user
     */
    List<UserRepository> findByUserEmailAndStatusOrderByAnalyzedAtDesc(String userEmail, RepositoryStatus status);
    
    /**
     * Check if user has analyzed a specific repository
     */
    boolean existsByUserEmailAndUrlAndStatus(String userEmail, String url, RepositoryStatus status);
    
    /**
     * Count repositories for a user
     */
    long countByUserEmail(String userEmail);
    
    /**
     * Count completed repositories for a user
     */
    long countByUserEmailAndStatus(String userEmail, RepositoryStatus status);
}