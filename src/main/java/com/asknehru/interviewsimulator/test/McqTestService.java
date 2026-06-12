package com.asknehru.interviewsimulator.test;
import com.asknehru.interviewsimulator.ai.LlmService;
import com.asknehru.interviewsimulator.interview.EvaluationRepository;
import com.asknehru.interviewsimulator.interview.AnswerRepository;
import com.asknehru.interviewsimulator.syllabus.TopicRepository;
import com.asknehru.interviewsimulator.interview.InterviewRepository;
import com.asknehru.interviewsimulator.core.GeneratedContentCacheRepository;
import com.asknehru.interviewsimulator.interview.QuestionRepository;
import com.asknehru.interviewsimulator.interview.Evaluation;
import com.asknehru.interviewsimulator.core.GeneratedContentCache;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.syllabus.Topic;
import com.asknehru.interviewsimulator.interview.Question;
import com.asknehru.interviewsimulator.interview.Answer;
import com.asknehru.interviewsimulator.interview.Interview;

import com.asknehru.interviewsimulator.test.dto.StartMcqTestRequest;
import com.asknehru.interviewsimulator.test.dto.SubmitMcqTestRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class McqTestService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationRepository evaluationRepository;
    private final LlmService llmService;
    private final TopicRepository topicRepository;
    private final GeneratedContentCacheRepository generatedContentCacheRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> generateMcqTest(User user, StartMcqTestRequest request) {
        // 1. Create and save Interview
        Interview interview = Interview.builder()
                .user(user)
                .topic(request.getTopic())
                .roundType(Interview.RoundType.MCQ)
                .status(Interview.Status.IN_PROGRESS)
                .currentQuestionIndex(0)
                .build();
        interview = interviewRepository.save(interview);

        List<Question> questions = new ArrayList<>();

        String customDescriptionPrompt = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? String.format("\nAdditional format/topic styling instructions: %s\n", request.getDescription())
                : "";

        String prompt = String.format(
                "Generate exactly 15 high-quality, challenging multiple-choice questions (MCQs) on the topic: '%s'.\n" +
                "The difficulty level of the questions should be: %s.%s\n\n" +
                "Output schema:\n" +
                "{\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question\": \"question text\",\n" +
                "      \"suggested_answer\": \"exact correct option text\",\n" +
                "      \"mcq_options\": [\"option A\", \"option B\", \"option C\", \"option D\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "CRITICAL RULES FOR MCQ OPTIONS & DISTRACTORS:\n" +
                "1. Each question must have exactly 4 options, and the suggested_answer must match one of the options EXACTLY.\n" +
                "2. NO TRIVIAL OR UNRELATED DISTRACTORS: All options must be highly relevant, plausible, and directly related to the specific sub-domain of '%s'. For example, if the topic is 'Java 8', all options must be Java 8 concepts/keywords/features (e.g. Streams API, Optional class, CompletableFuture, etc.). DO NOT include general computer science concepts (like binary trees), hardware terms (like floppy disks), unrelated web technologies (like HTML tags/CSS), or basic general knowledge unless it is directly part of the topic.\n" +
                "3. CONTEXTUAL CONSISTENCY: The options must belong to the exact same conceptual category. For example, if the question asks about a feature introduced in a version, all options must be features from that or other versions of the same technology so they are confusing. If the question asks for a class name, all options must be plausible class names in the same API/package.\n" +
                "4. CHALLENGING & REALISTIC: Ensure the options are challenging and test actual depth of knowledge appropriate for %s difficulty. Distractors must look like highly plausible alternatives to someone who does not know the exact answer.\n\n" +
                "Return only raw JSON matching the output schema.",
                request.getTopic(), request.getDifficulty().name().toLowerCase(), customDescriptionPrompt,
                request.getTopic(), request.getDifficulty().name().toLowerCase()
        );

        Topic topicEntity = topicRepository.findByUserAndName(user, request.getTopic())
                .orElseGet(() -> topicRepository.save(Topic.builder()
                        .user(user)
                        .name(request.getTopic())
                        .description("Autogenerated default topic")
                        .build()));

        String cacheKey = request.getDifficulty().name();
        Optional<GeneratedContentCache> cacheOpt = generatedContentCacheRepository
                .findByTopicAndContentTypeAndKey(topicEntity, "MCQ_TEST", cacheKey);

        String raw = null;
        boolean fromCache = false;

        if (cacheOpt.isPresent()) {
            raw = cacheOpt.get().getContent();
            fromCache = true;
            log.info("Serving MCQ questions for topic '{}' (difficulty: {}) from database cache.", request.getTopic(), cacheKey);
        }

        try {
            if (raw == null) {
                raw = llmService.generate(prompt);
            }
            JsonNode parsed = llmService.safeJsonLoads(raw);
            JsonNode qsNode = parsed.path("questions");

            if (qsNode.isArray() && qsNode.size() > 0) {
                // Save to cache if we just generated it successfully
                if (!fromCache && raw != null && !raw.trim().isEmpty()) {
                    GeneratedContentCache cache = GeneratedContentCache.builder()
                            .user(user)
                            .topic(topicEntity)
                            .contentType("MCQ_TEST")
                            .key(cacheKey)
                            .content(raw)
                            .build();
                    generatedContentCacheRepository.save(cache);
                }

                int order = 1;
                for (JsonNode qNode : qsNode) {
                    List<String> options = new ArrayList<>();
                    JsonNode opts = qNode.path("mcq_options");
                    if (opts.isArray()) {
                        for (JsonNode opt : opts) {
                            options.add(opt.asText());
                        }
                    }

                    // Shuffle options with ThreadLocalRandom for uniform distribution
                    Collections.shuffle(options, java.util.concurrent.ThreadLocalRandom.current());

                    Question question = Question.builder()
                            .interview(interview)
                            .text(qNode.path("question").asText())
                            .suggestedAnswer(qNode.path("suggested_answer").asText())
                            .mcqOptions(objectMapper.writeValueAsString(options))
                            .difficulty(request.getDifficulty())
                            .order(order++)
                            .build();
                    questions.add(questionRepository.save(question));
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate/parse MCQ questions: {}", e.getMessage());
        }

        // Fallback if LLM failed or generated empty questions
        if (questions.isEmpty()) {
            throw new RuntimeException("Failed to generate MCQ questions from AI. Please try again.");
        }

        // 2. Prepare client safe response (exclude suggestedAnswer to prevent cheating)
        List<Map<String, Object>> questionListResponse = new ArrayList<>();
        for (Question q : questions) {
            List<String> options = List.of();
            try {
                options = objectMapper.readValue(q.getMcqOptions(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.error("Error reading mcq options: {}", e.getMessage());
            }

            Map<String, Object> qMap = new HashMap<>();
            qMap.put("question_id", q.getId());
            qMap.put("question", q.getText());
            qMap.put("mcq_options", options);
            qMap.put("difficulty", q.getDifficulty());
            qMap.put("question_number", q.getOrder());
            questionListResponse.add(qMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("interview_id", interview.getId());
        response.put("topic", interview.getTopic());
        response.put("difficulty", request.getDifficulty());
        response.put("total_questions", questions.size());
        response.put("questions", questionListResponse);

        return response;
    }

    @Transactional
    public Map<String, Object> submitMcqTest(Long interviewId, SubmitMcqTestRequest request) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));

        if (interview.getStatus() == Interview.Status.COMPLETED) {
            throw new RuntimeException("Test has already been completed and graded.");
        }

        List<Question> questions = questionRepository.findByInterviewOrderByOrderDescCreatedAtDesc(interview);
        // Sort questions ascending by order for processing
        questions.sort(Comparator.comparingInt(q -> q.getOrder() != null ? q.getOrder() : 0));

        Map<Long, Question> questionMap = new HashMap<>();
        for (Question q : questions) {
            questionMap.put(q.getId(), q);
        }

        int correctCount = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (SubmitMcqTestRequest.AnswerEntry entry : request.getAnswers()) {
            Question question = questionMap.get(entry.getQuestionId());
            if (question == null) continue;

            String userInput = entry.getAnswer();
            boolean isCorrect = false;

            if (question.getSuggestedAnswer() != null && userInput != null) {
                isCorrect = question.getSuggestedAnswer().trim().equalsIgnoreCase(userInput.trim());
            }

            if (isCorrect) {
                correctCount++;
            }

            // Save Answer
            Answer answer = Answer.builder()
                    .question(question)
                    .userInput(userInput)
                    .evaluationStatus(Answer.EvaluationStatus.COMPLETED)
                    .build();
            answer = answerRepository.save(answer);

            // Save Evaluation
            Evaluation evaluation = Evaluation.builder()
                    .answer(answer)
                    .score(isCorrect ? 100 : 0)
                    .strengths(isCorrect ? "Correct answer selected." : "Incorrect selection.")
                    .weaknesses("")
                    .improvements(isCorrect ? "" : "The correct option was: " + question.getSuggestedAnswer())
                    .build();
            evaluationRepository.save(evaluation);

            List<String> options = List.of();
            try {
                options = objectMapper.readValue(question.getMcqOptions(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.error("Error reading mcq options: {}", e.getMessage());
            }

            Map<String, Object> resultEntry = new HashMap<>();
            resultEntry.put("question_id", question.getId());
            resultEntry.put("question", question.getText());
            resultEntry.put("mcq_options", options);
            resultEntry.put("user_answer", userInput);
            resultEntry.put("correct_answer", question.getSuggestedAnswer());
            resultEntry.put("is_correct", isCorrect);
            results.add(resultEntry);
        }

        // Sort results by question order
        results.sort(Comparator.comparingInt(r -> {
            Question q = questionMap.get((Long) r.get("question_id"));
            return q != null && q.getOrder() != null ? q.getOrder() : 0;
        }));

        // Update interview status
        interview.setCurrentQuestionIndex(questions.size());
        interview.setStatus(Interview.Status.COMPLETED);
        interviewRepository.save(interview);

        double percentage = ((double) correctCount / questions.size()) * 100.0;

        Map<String, Object> response = new HashMap<>();
        response.put("interview_id", interview.getId());
        response.put("total_questions", questions.size());
        response.put("correct_answers", correctCount);
        response.put("score_percentage", percentage);
        response.put("results", results);

        return response;
    }


}
