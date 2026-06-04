package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.dto.ChatRequest;
import com.asknehru.interviewsimulator.dto.ChatResponse;
import com.asknehru.interviewsimulator.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final LlmService llmService;

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String responseText = llmService.generateWithMessages(request.getMessages());
        return ResponseEntity.ok(new ChatResponse(responseText));
    }
}
