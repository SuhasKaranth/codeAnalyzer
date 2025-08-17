package com.suhas.codeAnalyzer.chat.ollama;

import java.util.ArrayList;
import java.util.List;

public class OllamaRequest {
    private String model;
    private List<OllamaMessage> messages;
    private boolean stream = false;
    private double temperature = 0.1;

    // Default constructor for Jackson
    public OllamaRequest() {
        this.messages = new ArrayList<>();
    }

    private OllamaRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages != null ? new ArrayList<>(builder.messages) : new ArrayList<>();
        this.stream = builder.stream;
        this.temperature = builder.temperature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<OllamaMessage> messages;
        private boolean stream = false;
        private double temperature = 0.1;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<OllamaMessage> messages) {
            this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OllamaRequest build() {
            return new OllamaRequest(this);
        }
    }

    // Getters and Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<OllamaMessage> getMessages() { return new ArrayList<>(messages); }
    public void setMessages(List<OllamaMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}