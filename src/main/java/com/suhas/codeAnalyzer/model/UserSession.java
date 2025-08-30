package com.suhas.codeAnalyzer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "user_sessions")
public class UserSession {
    
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String currentRepository;
    private String currentRepositoryId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private List<ChatExchange> conversationHistory;
    
    // Inner class for chat exchanges
    public static class ChatExchange {
        private String userMessage;
        private String assistantResponse;
        private LocalDateTime timestamp;
        
        public ChatExchange() {}
        
        public ChatExchange(String userMessage, String assistantResponse) {
            this.userMessage = userMessage;
            this.assistantResponse = assistantResponse;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters and Setters
        public String getUserMessage() {
            return userMessage;
        }
        
        public void setUserMessage(String userMessage) {
            this.userMessage = userMessage;
        }
        
        public String getAssistantResponse() {
            return assistantResponse;
        }
        
        public void setAssistantResponse(String assistantResponse) {
            this.assistantResponse = assistantResponse;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
    
    // Constructors
    public UserSession() {
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.conversationHistory = new ArrayList<>();
    }
    
    public UserSession(String userEmail) {
        this();
        this.userEmail = userEmail;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getCurrentRepository() {
        return currentRepository;
    }
    
    public void setCurrentRepository(String currentRepository) {
        this.currentRepository = currentRepository;
    }
    
    public String getCurrentRepositoryId() {
        return currentRepositoryId;
    }
    
    public void setCurrentRepositoryId(String currentRepositoryId) {
        this.currentRepositoryId = currentRepositoryId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastActivity() {
        return lastActivity;
    }
    
    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }
    
    public List<ChatExchange> getConversationHistory() {
        return conversationHistory;
    }
    
    public void setConversationHistory(List<ChatExchange> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
    
    // Helper methods
    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    public void addExchange(String userMessage, String assistantResponse) {
        this.conversationHistory.add(new ChatExchange(userMessage, assistantResponse));
        updateLastActivity();
    }
    
    public List<ChatExchange> getRecentExchanges(int limit) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return new ArrayList<>();
        }
        
        int fromIndex = Math.max(0, conversationHistory.size() - limit);
        return conversationHistory.subList(fromIndex, conversationHistory.size());
    }
    
    @Override
    public String toString() {
        return "UserSession{" +
                "id='" + id + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", currentRepository='" + currentRepository + '\'' +
                ", lastActivity=" + lastActivity +
                ", exchangeCount=" + (conversationHistory != null ? conversationHistory.size() : 0) +
                '}';
    }
}