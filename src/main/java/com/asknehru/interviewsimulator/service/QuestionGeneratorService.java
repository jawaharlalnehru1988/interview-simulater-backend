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
            return fallbackQuestion(topic, roundType, previousQuestions == null ? 0 : previousQuestions.size());
        }

        List<String> options = new ArrayList<>();
        JsonNode optsNode = parsed.path("mcq_options");
        if (optsNode.isArray()) {
            for (JsonNode opt : optsNode) {
                options.add(opt.asText());
            }
        }

        return GeneratedQuestion.builder()
                .question(parsed.path("question").asText("Explain " + topic))
                .suggestedAnswer(parsed.path("suggested_answer").asText(""))
                .mcqOptions(options)
                .build();
    }

    private GeneratedQuestion fallbackQuestion(String topic, Interview.RoundType roundType, int count) {
        int index = count + 1;
        int variation = count % 5;
        
        if (roundType == Interview.RoundType.MCQ) {
            String qText;
            List<String> options;
            String answer;
            switch (variation) {
                case 1:
                    qText = "What is the primary benefit of " + topic + " in modern architecture?";
                    options = List.of("A) Increased latency", "B) Better modularity", "C) Higher disk usage", "D) None of the above");
                    answer = "B";
                    break;
                case 2:
                    qText = "Which of the following is a common anti-pattern in " + topic + "?";
                    options = List.of("A) Loose coupling", "B) High cohesion", "C) God objects", "D) Separation of concerns");
                    answer = "C";
                    break;
                case 3:
                    qText = "How does " + topic + " handle concurrent requests?";
                    options = List.of("A) It crashes", "B) Using thread pools or event loops", "C) By ignoring them", "D) By deleting data");
                    answer = "B";
                    break;
                case 4:
                    qText = "What is the standard way to secure " + topic + "?";
                    options = List.of("A) Plaintext passwords", "B) Hardcoded keys", "C) OAuth2/JWT", "D) MD5 hashing");
                    answer = "C";
                    break;
                case 0:
                default:
                    qText = "Which choice best represents a key goal in " + topic + "?";
                    options = List.of("A) Increase accidental complexity", "B) Improve reliability and maintainability", "C) Remove all monitoring", "D) Avoid scalability planning");
                    answer = "B";
                    break;
            }
            return GeneratedQuestion.builder()
                    .question("Question " + index + ": " + qText)
                    .suggestedAnswer(answer)
                    .mcqOptions(options)
                    .build();
        }

        String qTextTechnical;
        switch (variation) {
            case 1: qTextTechnical = "Describe the lifecycle of a request in " + topic + "."; break;
            case 2: qTextTechnical = "What are the performance implications of using " + topic + "?"; break;
            case 3: qTextTechnical = "How would you design a scalable system using " + topic + "?"; break;
            case 4: qTextTechnical = "Explain the security best practices for " + topic + "."; break;
            case 0:
            default: qTextTechnical = "Explain a key concept in " + topic + " and how you would apply it in production."; break;
        }

        return GeneratedQuestion.builder()
                .question("Question " + index + ": " + qTextTechnical)
                .suggestedAnswer("I would focus on scalability, maintainability, and following best practices.")
                .mcqOptions(List.of())
                .build();
    }
}
