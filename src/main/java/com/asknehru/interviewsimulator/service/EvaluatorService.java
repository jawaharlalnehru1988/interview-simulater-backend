package com.asknehru.interviewsimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluatorService {

    private final LlmService llmService;

    public Map<String, Object> evaluate(String question, String answer, com.asknehru.interviewsimulator.model.Interview.RoundType roundType) {
        String schemaDetails;
        if (roundType == com.asknehru.interviewsimulator.model.Interview.RoundType.MCQ) {
            schemaDetails = "{\n" +
                "  \"score\": 0-100 integer (100 if correct option, 0 if wrong),\n" +
                "  \"strengths\": [\"string\"] (Provide exactly ONE string explaining why the selected answer is correct or incorrect),\n" +
                "  \"weaknesses\": [],\n" +
                "  \"improvements\": []\n" +
                "}";
        } else {
            schemaDetails = "{\n" +
                "  \"score\": 0-100 integer,\n" +
                "  \"strengths\": [\"string\"],\n" +
                "  \"weaknesses\": [\"string\"],\n" +
                "  \"improvements\": [\"string\"]\n" +
                "}";
        }

        String prompt = String.format(
            "Evaluate this interview answer and return strict JSON.\n\n" +
            "Question: %s\n" +
            "Answer: %s\n\n" +
            "Output schema:\n%s",
            question, answer, schemaDetails
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        if (parsed.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to parse model evaluation from AI."
            );
        }

        int score = parsed.path("score").asInt();
        score = Math.max(0, Math.min(score, 100));

        return Map.of(
            "score", score,
            "strengths", normalizeList(parsed.path("strengths")),
            "weaknesses", normalizeList(parsed.path("weaknesses")),
            "improvements", normalizeList(parsed.path("improvements"))
        );
    }

    private List<String> normalizeList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String val = item.asText().trim();
                if (!val.isEmpty()) {
                    result.add(val);
                }
            }
        } else if (node.isTextual()) {
            String val = node.asText().trim();
            if (!val.isEmpty()) {
                result.add(val);
            }
        }
        return result;
    }
}
