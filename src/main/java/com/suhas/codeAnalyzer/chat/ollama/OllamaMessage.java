package com.suhas.codeAnalyzer.chat.ollama;

public class OllamaMessage {
    private String role;
    private String content;

    // Default constructor for Jackson
    public OllamaMessage() {}

    public OllamaMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static OllamaMessage system(String content) {
        return new OllamaMessage("system", content);
    }

    public static OllamaMessage user(String content) {
        return new OllamaMessage("user", content);
    }

    // Getters and Setters
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}