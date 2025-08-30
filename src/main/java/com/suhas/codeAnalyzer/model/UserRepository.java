package com.suhas.codeAnalyzer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "user_repositories")
@CompoundIndex(name = "user_repo_idx", def = "{'userEmail': 1, 'url': 1}", unique = true)
public class UserRepository {
    
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String url;
    private String name;
    private String owner;
    private String branch;
    private RepositoryStatus status;
    private String chromaCollectionName;
    private String localPath;
    private LocalDateTime createdAt;
    private LocalDateTime analyzedAt;
    private Integer fileCount;
    private Map<String, Object> metadata;
    
    public enum RepositoryStatus {
        CLONED, ANALYZING, COMPLETED, FAILED
    }
    
    // Constructors
    public UserRepository() {
        this.createdAt = LocalDateTime.now();
        this.status = RepositoryStatus.CLONED;
    }
    
    public UserRepository(String userEmail, String url) {
        this();
        this.userEmail = userEmail;
        this.url = url;
        this.name = extractRepositoryName(url);
        this.owner = extractOwnerName(url);
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
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public RepositoryStatus getStatus() {
        return status;
    }
    
    public void setStatus(RepositoryStatus status) {
        this.status = status;
    }
    
    public String getChromaCollectionName() {
        return chromaCollectionName;
    }
    
    public void setChromaCollectionName(String chromaCollectionName) {
        this.chromaCollectionName = chromaCollectionName;
    }
    
    public String getLocalPath() {
        return localPath;
    }
    
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }
    
    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
    
    public Integer getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    // Helper methods
    public void markAsAnalyzed() {
        this.status = RepositoryStatus.COMPLETED;
        this.analyzedAt = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = RepositoryStatus.FAILED;
    }
    
    public boolean isAnalyzed() {
        return status == RepositoryStatus.COMPLETED;
    }
    
    private String extractRepositoryName(String url) {
        if (url == null || url.isEmpty()) return null;
        
        try {
            // Extract from https://github.com/owner/repo or https://github.com/owner/repo.git
            String[] parts = url.replace("https://github.com/", "")
                                .replace("http://github.com/", "")
                                .replace(".git", "")
                                .split("/");
            
            return parts.length >= 2 ? parts[1] : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private String extractOwnerName(String url) {
        if (url == null || url.isEmpty()) return null;
        
        try {
            // Extract from https://github.com/owner/repo
            String[] parts = url.replace("https://github.com/", "")
                                .replace("http://github.com/", "")
                                .replace(".git", "")
                                .split("/");
            
            return parts.length >= 1 ? parts[0] : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "UserRepository{" +
                "id='" + id + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", url='" + url + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", analyzedAt=" + analyzedAt +
                '}';
    }
}