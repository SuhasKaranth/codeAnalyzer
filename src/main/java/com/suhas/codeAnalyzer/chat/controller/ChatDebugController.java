package com.suhas.codeAnalyzer.chat.controller;

import com.suhas.codeAnalyzer.chat.model.ConversationContext;
import com.suhas.codeAnalyzer.chat.model.LLMResponse;
import com.suhas.codeAnalyzer.chat.service.ChatLLMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/debug")
public class ChatDebugController {

    @Autowired
    private ChatLLMService chatLLMService;

    @Autowired
    private com.suhas.codeAnalyzer.chat.service.ChatAgentService chatAgentService;

    @GetMapping("/test-patterns")
    public ResponseEntity<Map<String, Object>> testPatterns() {
        Map<String, Object> results = new HashMap<>();
        ConversationContext context = new ConversationContext("test-session");

        // Test different patterns
        String[] testMessages = {
                "Hello",
                "What can you help me with?",
                "Analyze https://github.com/spring-projects/spring-boot",
                "What endpoints does it have?",
                "Show me the components",
                "Check health",
                "List files",
                "Search for authentication"
        };

        for (String message : testMessages) {
            LLMResponse response = chatLLMService.callWithContext(message, context);
            Map<String, Object> testResult = new HashMap<>();
            testResult.put("action", response.getAction());
            testResult.put("response", response.getResponse());
            testResult.put("suggestions", response.getSuggestions());

            results.put(message, testResult);
        }

        return ResponseEntity.ok(results);
    }

    @PostMapping("/test-single")
    public ResponseEntity<LLMResponse> testSingle(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        ConversationContext context = new ConversationContext("test-session");

        LLMResponse response = chatLLMService.callWithContext(message, context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionContext(@PathVariable String sessionId) {
        ConversationContext context = chatAgentService.getContext(sessionId);
        Map<String, Object> result = new HashMap<>();
        
        if (context == null) {
            result.put("exists", false);
            result.put("message", "Session not found");
        } else {
            result.put("exists", true);
            result.put("sessionId", context.getSessionId());
            result.put("currentRepository", context.getCurrentRepository());
            result.put("currentRepositoryId", context.getCurrentRepositoryId());
            result.put("exchangeCount", context.getRecentExchanges().size());
            result.put("lastActivity", context.getLastActivity());
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/session/{sessionId}/set-repo")
    public ResponseEntity<Map<String, Object>> setRepositoryContext(
            @PathVariable String sessionId, 
            @RequestBody Map<String, String> request) {
        
        String repositoryUrl = request.get("repositoryUrl");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "repositoryUrl is required");
            return ResponseEntity.badRequest().body(error);
        }
        
        chatAgentService.setRepositoryContext(sessionId, repositoryUrl);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("repositoryUrl", repositoryUrl);
        result.put("message", "Repository context set successfully");
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/test-slm-intent")
    public ResponseEntity<Map<String, Object>> testSLMIntent(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "test-session");
        String repoUrl = request.get("repositoryUrl");
        
        ConversationContext context = new ConversationContext(sessionId);
        if (repoUrl != null && !repoUrl.isEmpty()) {
            context.setCurrentRepository(repoUrl);
        }
        
        // Test SLM intent analysis directly
        String slmIntent = chatLLMService.analyzePotentialIntentWithSLM(message, context);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("sessionId", sessionId);
        result.put("repositoryContext", context.getCurrentRepository());
        result.put("slmIntent", slmIntent);
        
        // Also test full processing
        LLMResponse fullResponse = chatLLMService.callWithContext(message, context);
        result.put("fullProcessingAction", fullResponse.getAction());
        result.put("fullProcessingResponse", fullResponse.getResponse());
        
        return ResponseEntity.ok(result);
    }
}