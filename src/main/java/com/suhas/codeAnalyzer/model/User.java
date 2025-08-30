package com.suhas.codeAnalyzer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "users")
public class User {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String email;
    
    private LocalDateTime createdAt;
    private LocalDateTime lastVisit;
    private Map<String, Object> preferences;
    
    // Constructors
    public User() {
        this.createdAt = LocalDateTime.now();
        this.lastVisit = LocalDateTime.now();
    }
    
    public User(String email) {
        this();
        this.email = email;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastVisit() {
        return lastVisit;
    }
    
    public void setLastVisit(LocalDateTime lastVisit) {
        this.lastVisit = lastVisit;
    }
    
    public Map<String, Object> getPreferences() {
        return preferences;
    }
    
    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
    }
    
    // Helper method
    public void updateLastVisit() {
        this.lastVisit = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                ", lastVisit=" + lastVisit +
                '}';
    }
}