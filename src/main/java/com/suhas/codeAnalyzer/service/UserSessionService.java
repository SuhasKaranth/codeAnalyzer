package com.suhas.codeAnalyzer.service;

import com.suhas.codeAnalyzer.model.User;
import com.suhas.codeAnalyzer.model.UserSession;
import com.suhas.codeAnalyzer.repository.UserSessionRepository;
import com.suhas.codeAnalyzer.repository.UserRepositoryDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserSessionService {
    
    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);
    
    @Autowired
    private com.suhas.codeAnalyzer.repository.UserRepository userRepository;
    
    @Autowired
    private UserSessionRepository userSessionRepository;
    
    @Autowired
    private UserRepositoryDataRepository userRepositoryDataRepository;
    
    /**
     * Initialize or resume user session
     */
    public SessionInitResult initializeUserSession(String email) {
        log.info("Initializing session for user: {}", email);
        
        // Get or create user
        User user = getOrCreateUser(email);
        
        // Get or create user session
        UserSession session = getOrCreateUserSession(email);
        
        // Get user's repositories
        List<com.suhas.codeAnalyzer.model.UserRepository> repositories = getUserRepositories(email);
        
        boolean isNewUser = user.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1));
        
        log.info("Session initialized for user: {} (isNew: {}, repositories: {})", 
                email, isNewUser, repositories.size());
        
        return new SessionInitResult(user, session, repositories, isNewUser);
    }
    
    /**
     * Get current session for user
     */
    public Optional<UserSession> getCurrentSession(String email) {
        return userSessionRepository.findTopByUserEmailOrderByLastActivityDesc(email);
    }
    
    /**
     * Update session repository context
     */
    public void updateSessionRepository(String email, String repositoryUrl, String repositoryId) {
        log.info("Updating session repository for {}: {}", email, repositoryUrl);
        
        UserSession session = getOrCreateUserSession(email);
        session.setCurrentRepository(repositoryUrl);
        session.setCurrentRepositoryId(repositoryId);
        session.updateLastActivity();
        
        userSessionRepository.save(session);
        
        log.info("Session repository updated for user: {}", email);
    }
    
    /**
     * Add conversation exchange to session
     */
    public void addConversationExchange(String email, String userMessage, String assistantResponse) {
        UserSession session = getOrCreateUserSession(email);
        session.addExchange(userMessage, assistantResponse);
        
        userSessionRepository.save(session);
        
        log.debug("Added conversation exchange for user: {} (total exchanges: {})", 
                email, session.getConversationHistory().size());
    }
    
    /**
     * Get user's repositories
     */
    public List<com.suhas.codeAnalyzer.model.UserRepository> getUserRepositories(String email) {
        return userRepositoryDataRepository.findByUserEmailOrderByAnalyzedAtDesc(email);
    }
    
    /**
     * Get completed repositories for user
     */
    public List<com.suhas.codeAnalyzer.model.UserRepository> getCompletedRepositories(String email) {
        return userRepositoryDataRepository.findByUserEmailAndStatusOrderByAnalyzedAtDesc(
                email, com.suhas.codeAnalyzer.model.UserRepository.RepositoryStatus.COMPLETED);
    }
    
    /**
     * Create or update user repository
     */
    public com.suhas.codeAnalyzer.model.UserRepository createOrUpdateUserRepository(String email, String repositoryUrl) {
        Optional<com.suhas.codeAnalyzer.model.UserRepository> existing = userRepositoryDataRepository.findByUserEmailAndUrl(email, repositoryUrl);
        
        if (existing.isPresent()) {
            log.info("Repository already exists for user {}: {}", email, repositoryUrl);
            return existing.get();
        }
        
        com.suhas.codeAnalyzer.model.UserRepository userRepo = new com.suhas.codeAnalyzer.model.UserRepository(email, repositoryUrl);
        userRepo = userRepositoryDataRepository.save(userRepo);
        
        log.info("Created new user repository: {} for user: {}", repositoryUrl, email);
        return userRepo;
    }
    
    /**
     * Mark repository as analyzed
     */
    public void markRepositoryAsAnalyzed(String email, String repositoryUrl, String chromaCollectionName, Integer fileCount) {
        Optional<com.suhas.codeAnalyzer.model.UserRepository> repoOpt = userRepositoryDataRepository.findByUserEmailAndUrl(email, repositoryUrl);
        
        if (repoOpt.isPresent()) {
            com.suhas.codeAnalyzer.model.UserRepository repo = repoOpt.get();
            repo.markAsAnalyzed();
            repo.setChromaCollectionName(chromaCollectionName);
            repo.setFileCount(fileCount);
            
            userRepositoryDataRepository.save(repo);
            log.info("Marked repository as analyzed: {} for user: {}", repositoryUrl, email);
        }
    }
    
    /**
     * Check if user has analyzed a repository
     */
    public boolean hasUserAnalyzedRepository(String email, String repositoryUrl) {
        return userRepositoryDataRepository.existsByUserEmailAndUrlAndStatus(
                email, repositoryUrl, com.suhas.codeAnalyzer.model.UserRepository.RepositoryStatus.COMPLETED);
    }
    
    // Private helper methods
    private User getOrCreateUser(String email) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.updateLastVisit();
            return userRepository.save(user);
        }
        
        User newUser = new User(email);
        User savedUser = userRepository.save(newUser);
        log.info("Created new user: {}", email);
        return savedUser;
    }
    
    private UserSession getOrCreateUserSession(String email) {
        Optional<UserSession> existingSession = userSessionRepository.findTopByUserEmailOrderByLastActivityDesc(email);
        
        if (existingSession.isPresent()) {
            UserSession session = existingSession.get();
            session.updateLastActivity();
            return userSessionRepository.save(session);
        }
        
        UserSession newSession = new UserSession(email);
        UserSession savedSession = userSessionRepository.save(newSession);
        log.info("Created new session for user: {}", email);
        return savedSession;
    }
    
    // Result class for session initialization
    public static class SessionInitResult {
        private final User user;
        private final UserSession session;
        private final List<com.suhas.codeAnalyzer.model.UserRepository> repositories;
        private final boolean isNewUser;
        
        public SessionInitResult(User user, UserSession session, List<com.suhas.codeAnalyzer.model.UserRepository> repositories, boolean isNewUser) {
            this.user = user;
            this.session = session;
            this.repositories = repositories;
            this.isNewUser = isNewUser;
        }
        
        public User getUser() { return user; }
        public UserSession getSession() { return session; }
        public List<com.suhas.codeAnalyzer.model.UserRepository> getRepositories() { return repositories; }
        public boolean isNewUser() { return isNewUser; }
    }
}