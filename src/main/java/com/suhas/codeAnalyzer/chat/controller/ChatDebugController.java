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
}