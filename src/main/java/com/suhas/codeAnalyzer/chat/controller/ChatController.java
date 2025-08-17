package com.suhas.codeAnalyzer.chat.controller;

import com.suhas.codeAnalyzer.chat.model.ChatRequest;
import com.suhas.codeAnalyzer.chat.model.ChatResponse;
import com.suhas.codeAnalyzer.chat.model.ConversationContext;
import com.suhas.codeAnalyzer.chat.service.ChatAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // For development only
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatAgentService chatAgentService;

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            // Generate session ID if not provided
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            log.info("Received chat message for session: {}", sessionId);

            ChatResponse response = chatAgentService.processMessage(
                    request.getMessage(),
                    sessionId
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in chat controller", e);
            return ResponseEntity.ok(
                    ChatResponse.error("I'm having trouble processing your request. Please try again.",
                            request.getSessionId())
            );
        }
    }

    @GetMapping("/session/{sessionId}/context")
    public ResponseEntity<ConversationContext> getContext(@PathVariable String sessionId) {
        try {
            ConversationContext context = chatAgentService.getContext(sessionId);
            if (context != null) {
                return ResponseEntity.ok(context);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting context", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/session/new")
    public ResponseEntity<String> createNewSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        return ResponseEntity.ok(sessionId);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}