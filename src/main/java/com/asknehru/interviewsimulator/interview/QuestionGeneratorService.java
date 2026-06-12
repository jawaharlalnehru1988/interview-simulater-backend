package com.asknehru.interviewsimulator.interview;
import com.asknehru.interviewsimulator.ai.LlmService;

import com.asknehru.interviewsimulator.interview.dto.GeneratedQuestion;
import com.asknehru.interviewsimulator.interview.Interview;
import com.asknehru.interviewsimulator.interview.Question;
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
            "}\n\n" +
            "CRITICAL RULES FOR MCQ OPTIONS & DISTRACTORS (if round is MCQ):\n" +
            "1. Each question must have exactly 4 options, and the suggested_answer must match one of the options EXACTLY.\n" +
            "2. NO TRIVIAL OR UNRELATED DISTRACTORS: All options must be highly relevant, plausible, and directly related to the specific sub-domain of '%s'. For example, if the topic is 'Java 8', all options must be Java 8 concepts/keywords/features (e.g. Streams API, Optional class, CompletableFuture, etc.). DO NOT include general computer science concepts (like binary trees), hardware terms (like floppy disks), unrelated web technologies (like HTML tags/CSS), or basic general knowledge unless it is directly part of the topic.\n" +
            "3. CONTEXTUAL CONSISTENCY: The options must belong to the exact same conceptual category. For example, if the question asks about a feature introduced in a version, all options must be features from that or other versions of the same technology so they are confusing. If the question asks for a class name, all options must be plausible class names in the same API/package.\n" +
            "4. CHALLENGING & REALISTIC: Ensure the options are challenging and test actual depth of knowledge appropriate for %s difficulty. Distractors must look like highly plausible alternatives to someone who does not know the exact answer.",
            difficulty.name().toLowerCase(), roundType.name(), topic, previousQsText.toString(), topic, difficulty.name().toLowerCase()
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
