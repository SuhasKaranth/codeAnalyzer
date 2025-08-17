package com.suhas.codeAnalyzer.chat.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationContext {
    private final String sessionId;
    private String currentRepository;
    private String currentRepositoryId;
    private final List<ChatExchange> recentExchanges;
    private Instant lastActivity;
    private final Map<String, Object> sessionData;

    public ConversationContext(String sessionId) {
        this.sessionId = sessionId;
        this.recentExchanges = new ArrayList<>();
        this.lastActivity = Instant.now();
        this.sessionData = new HashMap<>();
    }

    public void addExchange(String userMessage, String assistantResponse) {
        this.recentExchanges.add(new ChatExchange(userMessage, assistantResponse, Instant.now()));
        this.lastActivity = Instant.now();

        // Keep only last 5 exchanges for context
        while (this.recentExchanges.size() > 5) {
            this.recentExchanges.remove(0);
        }
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public String getCurrentRepository() { return currentRepository; }
    public void setCurrentRepository(String currentRepository) { this.currentRepository = currentRepository; }
    public String getCurrentRepositoryId() { return currentRepositoryId; }
    public void setCurrentRepositoryId(String currentRepositoryId) { this.currentRepositoryId = currentRepositoryId; }
    public List<ChatExchange> getRecentExchanges() { return new ArrayList<>(recentExchanges); }
    public Instant getLastActivity() { return lastActivity; }
    public Map<String, Object> getSessionData() { return new HashMap<>(sessionData); }
}