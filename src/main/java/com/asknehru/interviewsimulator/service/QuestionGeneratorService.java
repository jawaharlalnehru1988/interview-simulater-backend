package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.dto.GeneratedQuestion;
import com.asknehru.interviewsimulator.model.Interview;
import com.asknehru.interviewsimulator.model.Question;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionGeneratorService {

    private final LlmService llmService;

    public GeneratedQuestion generate(String topic, Question.Difficulty difficulty, Interview.RoundType roundType, List<Question> previousQuestions) {
        StringBuilder previousQsText = new StringBuilder();
        if (previousQuestions != null && !previousQuestions.isEmpty()) {
            previousQsText.append("\nDo NOT ask any of the following questions as they have already been asked:\n");
            for (Question q : previousQuestions) {
                previousQsText.append("- ").append(q.getText()).append("\n");
            }
        }

        String prompt = String.format(
            "Generate a %s %s interview question for the topic: %s.%s\n\n" +
            "Output schema:\n" +
            "{\n" +
            "  \"question\": \"string\",\n" +
            "  \"suggested_answer\": \"string\",\n" +
            "  \"mcq_options\": [\"string\"] (if round is MCQ, provide exactly 4 options including the correct one, otherwise leave empty)\n" +
            "}",
            difficulty.name().toLowerCase(), roundType.name(), topic, previousQsText.toString()
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        if (parsed.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to generate question from AI."
            );
        }

        List<String> options = new ArrayList<>();
        JsonNode optsNode = parsed.path("mcq_options");
        if (optsNode.isArray()) {
            for (JsonNode opt : optsNode) {
                options.add(opt.asText());
            }
        }

        return GeneratedQuestion.builder()
                .question(parsed.path("question").asText())
                .suggestedAnswer(parsed.path("suggested_answer").asText())
                .mcqOptions(options)
                .build();
    }
}
