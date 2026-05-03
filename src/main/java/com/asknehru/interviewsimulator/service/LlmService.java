package com.asknehru.interviewsimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.max-retries}")
    private int maxRetries;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("LLM API Key is missing");
            return "";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.4);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    return root.path("choices").get(0).path("message").path("content").asText("");
                }
            } catch (Exception e) {
                log.error("LLM request failed on attempt {}: {}", attempt + 1, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return "";
    }

    public JsonNode safeJsonLoads(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            // Remove markdown code blocks if present
            String cleaned = rawText.replaceAll("```json|```", "").trim();
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse JSON from LLM: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}
