package com.suhas.codeAnalyzer.chat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/test")
public class ChatTestController {

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }
        return restTemplate;
    }

    @GetMapping("/ollama")
    public ResponseEntity<Map<String, Object>> testOllama() {
        Map<String, Object> response = new HashMap<>();
        try {
            String ollamaResponse = getRestTemplate().getForObject(ollamaBaseUrl + "/api/tags", String.class);
            response.put("status", "connected");
            response.put("response", ollamaResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, String>> testServices() {
        Map<String, String> services = new HashMap<>();
        services.put("chat", "available");
        services.put("codeAnalysis", "available");
        services.put("vectorStore", "available");
        return ResponseEntity.ok(services);
    }
}