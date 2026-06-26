package com.asknehru.interviewsimulator.test;
import com.asknehru.interviewsimulator.ai.LlmService;
import com.asknehru.interviewsimulator.interview.EvaluationRepository;
import com.asknehru.interviewsimulator.interview.AnswerRepository;
import com.asknehru.interviewsimulator.interview.InterviewRepository;
import com.asknehru.interviewsimulator.interview.QuestionRepository;
import com.asknehru.interviewsimulator.interview.Evaluation;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.interview.Question;
import com.asknehru.interviewsimulator.interview.Answer;
import com.asknehru.interviewsimulator.interview.Interview;
import com.asknehru.interviewsimulator.test.dto.StartCodingTestRequest;
import com.asknehru.interviewsimulator.test.dto.EvaluateManipulationRequest;
import com.asknehru.interviewsimulator.test.dto.SubmitCodingCodeRequest;
import com.asknehru.interviewsimulator.test.dto.SubmitCodingApproachRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodingTestService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationRepository evaluationRepository;
    private final com.asknehru.interviewsimulator.test.category.SavedQuestionSetRepository savedQuestionSetRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> generateCodingTest(User user, StartCodingTestRequest request) {
        // 1. Create and save Interview session
        Interview interview = Interview.builder()
                .user(user)
                .topic(request.getTopic())
                .roundType(Interview.RoundType.CODING)
                .status(Interview.Status.IN_PROGRESS)
                .currentQuestionIndex(0)
                .build();
        interview = interviewRepository.save(interview);

        String customPromptPart = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? String.format("\nSpecial requests or topics: %s\n", request.getDescription())
                : "";

        String prompt = "";
        Question.Difficulty difficulty = request.getDifficulty();

        if (difficulty == Question.Difficulty.SUPER_EASY) {
            prompt = String.format(
                    "Generate a code comprehension question for a coding interview on the topic: '%s'.\n" +
                    "Difficulty level: SUPER EASY (testing basic concepts, output tracing).\n%s\n" +
                    "Follow these rules:\n" +
                    "1. Provide a code snippet in %s that contains confusing logic, subtle operator precedence, variable scope quirks, inheritance behaviors, or intentional error codes that test candidate's deeper understanding of the programming language.\n" +
                    "2. Ask a clear question like: 'What will be the output of the code?' or 'Will this code compile? If so, what is the output, otherwise why does it fail?'\n" +
                    "3. CRITICAL CODE FORMATTING RULE: The code snippet in the `question` field must be written with proper indentation, standard spacing, and correct newlines (using `\\n`), and must be wrapped inside a Markdown code block with language specifier (e.g., \\n```java\\n[indented code]\\n```\\n or \\n```javascript\\n[indented code]\\n```\\n). Do NOT compact the code into a single line or remove newlines. Keep it highly readable and properly formatted like real-world editor code.\n" +
                    "4. Return raw JSON matching this output schema:\n" +
                    "{\n" +
                    "  \"question\": \"Question text including the code snippet in markdown block\",\n" +
                    "  \"suggested_answer\": \"Detailed explanation of why it compiles or fails, and what the correct output is\"\n" +
                    "}\n\n" +
                    "Return ONLY raw JSON.",
                    request.getTopic(), customPromptPart, request.getTopic()
            );
        } else if (difficulty == Question.Difficulty.EASY) {
            prompt = String.format(
                    "Generate a Time and Space Complexity calculation question for a coding interview on the topic: '%s'.\n" +
                    "Difficulty level: EASY (testing algorithmic execution estimation).\n%s\n" +
                    "Follow these rules:\n" +
                    "1. Provide a code snippet in %s illustrating an algorithm (e.g. recursion, sorting, nested loops, binary search, tree traversal).\n" +
                    "2. Ask the user to determine the Time Complexity and Space Complexity of the code (e.g. 'Determine the Big-O time and space complexity of the function below').\n" +
                    "3. CRITICAL CODE FORMATTING RULE: The code snippet in the `question` field must be written with proper indentation, standard spacing, and correct newlines (using `\\n`), and must be wrapped inside a Markdown code block with language specifier (e.g., \\n```java\\n[indented code]\\n```\\n or \\n```javascript\\n[indented code]\\n```\\n). Do NOT compact the code into a single line or remove newlines. Keep it highly readable and properly formatted like real-world editor code.\n" +
                    "4. Return raw JSON matching this output schema:\n" +
                    "{\n" +
                    "  \"question\": \"Question text including the code snippet in a markdown block\",\n" +
                    "  \"suggested_answer\": \"Detailed breakdown of time and space complexity (e.g., O(N) time, O(1) space) and why\"\n" +
                    "}\n\n" +
                    "Return ONLY raw JSON.",
                    request.getTopic(), customPromptPart, request.getTopic()
            );
        } else {
            // MEDIUM, HARD, HARDER
            String diffName = difficulty.name().toLowerCase();
            prompt = String.format(
                    "Generate exactly one LeetCode-style programming problem on the topic: '%s'.\n" +
                    "Difficulty level: %s.%s\n" +
                    "Follow these rules:\n" +
                    "1. The problem must involve data structures or algorithms suitable for %s difficulty (e.g., string manipulation, arrays, trees, heaps, dynamic programming, backtracking, scenario-based system algorithms).\n" +
                    "2. Include: Problem Statement, Input Format, Output Format, Constraints, and at least 2 Sample Test Cases (Input/Output).\n" +
                    "3. Return raw JSON matching this output schema:\n" +
                    "{\n" +
                    "  \"question\": \"Problem description text with constraints and test cases formatted in markdown\",\n" +
                    "  \"suggested_answer\": \"optimal algorithm explanation, complexity analysis (e.g. O(N log N) time, O(N) space), and reference optimal solution code\"\n" +
                    "}\n\n" +
                    "Return ONLY raw JSON.",
                    request.getTopic(), diffName, customPromptPart, diffName
            );
        }

        Question question;
        try {
            String raw = llmService.generate(prompt);
            JsonNode parsed = llmService.safeJsonLoads(raw);

            question = Question.builder()
                    .interview(interview)
                    .text(parsed.path("question").asText("Could not generate coding challenge. Please try again."))
                    .suggestedAnswer(parsed.path("suggested_answer").asText("Optimal solution reference."))
                    .difficulty(difficulty)
                    .order(1)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate coding challenge from LLM: {}", e.getMessage());
            throw new RuntimeException("Failed to generate coding challenge from AI. Please try again.", e);
        }

        question = questionRepository.save(question);
        interview.setCurrentQuestionIndex(1);
        interviewRepository.save(interview);

        Map<String, Object> response = new HashMap<>();
        response.put("interview_id", interview.getId());
        response.put("topic", interview.getTopic());
        response.put("difficulty", difficulty);
        response.put("question_id", question.getId());
        response.put("question_text", question.getText());

        return response;
    }

    @Transactional
    public Map<String, Object> submitApproach(Long interviewId, Long questionId, SubmitCodingApproachRequest request) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        String prompt = String.format(
                "Evaluate the candidate's proposed algorithmic approach for the following coding question.\n\n" +
                "Question:\n%s\n\n" +
                "Candidate's Approach:\n%s\n\n" +
                "Decide whether this approach is viable, correct, and optimal enough to begin coding. " +
                "Provide constructive feedback. If the approach has fundamental issues, suggest a hint or another way to think but do NOT solve it fully for them.\n\n" +
                "Return raw JSON:\n" +
                "{\n" +
                "  \"approved\": true or false,\n" +
                "  \"feedback\": \"Constructive critique, encouragement, or redirection hints\"\n" +
                "}",
                question.getText(), request.getApproach()
        );

        boolean approved = false;
        String feedback = "";

        try {
            String raw = llmService.generate(prompt);
            JsonNode parsed = llmService.safeJsonLoads(raw);
            approved = parsed.path("approved").asBoolean(false);
            feedback = parsed.path("feedback").asText("Please refine your approach.");
        } catch (Exception e) {
            log.error("LLM approach evaluation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to evaluate coding approach using AI. Please try again.", e);
        }

        // Save approach as an Answer entry for logging
        Answer answer = Answer.builder()
                .question(question)
                .userInput("Approach: " + request.getApproach())
                .evaluationStatus(Answer.EvaluationStatus.COMPLETED)
                .build();
        answer = answerRepository.save(answer);

        Evaluation evaluation = Evaluation.builder()
                .answer(answer)
                .score(approved ? 100 : 0)
                .strengths(approved ? "Valid approach proposed." : "")
                .weaknesses(!approved ? "Approach has conceptual/optimal gaps." : "")
                .improvements(feedback)
                .build();
        evaluationRepository.save(evaluation);

        Map<String, Object> response = new HashMap<>();
        response.put("approved", approved);
        response.put("feedback", feedback);

        if (approved) {
            // Generate starter code boilerplates for Java and Javascript
            String codeTemplatesPrompt = String.format(
                    "Create starter code boilerplates/function signatures for Java and JavaScript to solve this problem.\n\n" +
                    "Question:\n%s\n\n" +
                    "Return ONLY raw JSON matching this schema:\n" +
                    "{\n" +
                    "  \"java\": \"java class and method template\",\n" +
                    "  \"javascript\": \"javascript function template\"\n" +
                    "}",
                    question.getText()
            );

            try {
                String templatesRaw = llmService.generate(codeTemplatesPrompt);
                JsonNode templatesParsed = llmService.safeJsonLoads(templatesRaw);
                response.put("java_template", templatesParsed.path("java").asText("// Java template"));
                response.put("javascript_template", templatesParsed.path("javascript").asText("// Javascript template"));
            } catch (Exception e) {
                log.error("Failed to generate code templates: {}", e.getMessage());
                response.put("java_template", "public class Solution {\n    public void solve() {\n        // Write code here\n    }\n}");
                response.put("javascript_template", "function solve() {\n    // Write code here\n}");
            }
        }

        return response;
    }

    @Transactional
    public Map<String, Object> submitDirectAnswer(Long interviewId, Long questionId, String userInput) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        String prompt = String.format(
                "Evaluate the user's answer to this code output/complexity question.\n\n" +
                "Question:\n%s\n\n" +
                "Expected Answer Details:\n%s\n\n" +
                "User's Answer:\n%s\n\n" +
                "Return raw JSON:\n" +
                "{\n" +
                "  \"score\": 0 to 100 integer,\n" +
                "  \"explanation\": \"Detailed explanation of why the user's answer is correct or incorrect, explaining the logic\"\n" +
                "}",
                question.getText(), question.getSuggestedAnswer(), userInput
        );

        int score = 0;
        String explanation = "";

        try {
            String raw = llmService.generate(prompt);
            JsonNode parsed = llmService.safeJsonLoads(raw);
            score = parsed.path("score").asInt(0);
            explanation = parsed.path("explanation").asText("");
        } catch (Exception e) {
            log.error("Failed to evaluate direct answer: {}", e.getMessage());
            throw new RuntimeException("Failed to evaluate direct answer using AI. Please try again.", e);
        }

        Answer answer = Answer.builder()
                .question(question)
                .userInput(userInput)
                .evaluationStatus(Answer.EvaluationStatus.COMPLETED)
                .build();
        answer = answerRepository.save(answer);

        Evaluation evaluation = Evaluation.builder()
                .answer(answer)
                .score(score)
                .strengths(score >= 70 ? "Correct interpretation of snippet rules." : "")
                .weaknesses(score < 70 ? "Miscalculated code state flow." : "")
                .improvements(explanation)
                .build();
        evaluationRepository.save(evaluation);

        interview.setStatus(Interview.Status.COMPLETED);
        interviewRepository.save(interview);

        Map<String, Object> response = new HashMap<>();
        response.put("score", score);
        response.put("explanation", explanation);
        response.put("correct_answer", question.getSuggestedAnswer());

        return response;
    }

    @Transactional
    public Map<String, Object> submitCode(Long interviewId, Long questionId, SubmitCodingCodeRequest request) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        String prompt = String.format(
                "Verify and evaluate this candidate solution code for correctness, time/space complexity, and best practices.\n\n" +
                "Question:\n%s\n\n" +
                "Language used: %s\n\n" +
                "Candidate's Code:\n%s\n\n" +
                "Return raw JSON:\n" +
                "{\n" +
                "  \"score\": 0 to 100 integer,\n" +
                "  \"strengths\": [\"Bullet points explaining what they did well\"],\n" +
                "  \"weaknesses\": [\"Bullet points showing bugs, unhandled edge cases, or sub-optimal choices\"],\n" +
                "  \"improvements\": [\"Refinement actions, performance tips, complexity comments\"],\n" +
                "  \"refactored_code\": \"A clean, optimal, well-commented, production-grade refactoring of the user's code\"\n" +
                "}",
                question.getText(), request.getLanguage(), request.getCode()
        );

        Map<String, Object> response = new HashMap<>();
        try {
            String raw = llmService.generate(prompt);
            JsonNode parsed = llmService.safeJsonLoads(raw);

            int score = parsed.path("score").asInt(0);
            score = Math.max(0, Math.min(score, 100));

            // Normalize fields
            List<String> strengthsList = new ArrayList<>();
            parsed.path("strengths").forEach(node -> strengthsList.add(node.asText()));
            List<String> weaknessesList = new ArrayList<>();
            parsed.path("weaknesses").forEach(node -> weaknessesList.add(node.asText()));
            List<String> improvementsList = new ArrayList<>();
            parsed.path("improvements").forEach(node -> improvementsList.add(node.asText()));
            String refactored = parsed.path("refactored_code").asText("// Refactored solution");

            Answer answer = Answer.builder()
                    .question(question)
                    .userInput(request.getCode())
                    .evaluationStatus(Answer.EvaluationStatus.COMPLETED)
                    .build();
            answer = answerRepository.save(answer);

            Evaluation evaluation = Evaluation.builder()
                    .answer(answer)
                    .score(score)
                    .strengths(objectMapper.writeValueAsString(strengthsList))
                    .weaknesses(objectMapper.writeValueAsString(weaknessesList))
                    .improvements(objectMapper.writeValueAsString(improvementsList))
                    .build();
            evaluationRepository.save(evaluation);

            interview.setStatus(Interview.Status.COMPLETED);
            interviewRepository.save(interview);

            response.put("score", score);
            response.put("strengths", strengthsList);
            response.put("weaknesses", weaknessesList);
            response.put("improvements", improvementsList);
            response.put("refactored_code", refactored);
        } catch (Exception e) {
            log.error("Failed to grade candidate code solution: {}", e.getMessage());
            throw new RuntimeException("Failed to evaluate code solution using AI. Please try again.", e);
        }

        return response;
    }
    public List<String> generateManipulationQuestions(com.asknehru.interviewsimulator.test.dto.StartManipulationRequest request) {
        String topic = request.getTopic();
        String category = request.getCategory();
        String previousQuestionsInstruction = "";

        if (request.getPreviousQuestions() != null && !request.getPreviousQuestions().isEmpty()) {
            previousQuestionsInstruction = "CRITICAL RULE: Do NOT generate any questions that match or are highly similar to the following previously generated questions:\n"
                    + String.join("\n", request.getPreviousQuestions()) + "\n\n";
        }

        String prompt = String.format(
                "Generate exactly 10 short, sentence-formatted coding interview questions for the programming language '%s' and the category '%s'.\n" +
                "The questions should be easy to medium difficulty, focusing on string manipulation, array manipulation, searching, sorting, or object manipulation as appropriate for the category.\n" +
                "%s" +
                "Example format:\n" +
                "Write a function that takes a string as input and returns the number of vowels in the string.\n" +
                "Write a function that takes an array of numbers and returns the largest number.\n\n" +
                "Return the output STRICTLY as a raw JSON array of strings matching this output schema:\n" +
                "[\n" +
                "  \"question 1\",\n" +
                "  \"question 2\",\n" +
                "  ...\n" +
                "]\n\n" +
                "Return ONLY raw JSON, no markdown blocks or extra text.",
                topic, category, previousQuestionsInstruction
        );

        String llmResponse = llmService.generate(prompt);
        llmResponse = llmResponse.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            return objectMapper.readValue(llmResponse, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse LLM response for manipulation questions", e);
            throw new RuntimeException("Failed to generate manipulation questions.");
        }
    }

    @Transactional
    public Map<String, Object> evaluateManipulationAnswers(User user, EvaluateManipulationRequest request) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("You are an expert interviewer evaluating a candidate's code in %s for the category %s.\n", request.getTopic(), request.getCategory()));
        promptBuilder.append("The candidate was given 10 questions and has provided answers for each.\n\n");

        for (int i = 0; i < request.getQuestions().size(); i++) {
            promptBuilder.append(String.format("Question %d: %s\n", i + 1, request.getQuestions().get(i)));
            promptBuilder.append(String.format("Candidate's Approach:\n%s\n\n", request.getApproaches() != null && request.getApproaches().size() > i ? request.getApproaches().get(i) : "No approach provided."));
            promptBuilder.append(String.format("Candidate's Code:\n%s\n\n", request.getAnswers() != null && request.getAnswers().size() > i ? request.getAnswers().get(i) : "No code provided."));
        }

        promptBuilder.append("Evaluate both the candidate's approach and their code carefully.\n");
        promptBuilder.append("Score the approach out of 4 points (did they understand the problem, plan logical steps?).\n");
        promptBuilder.append("Score the code out of 6 points (does it compile/run, is it optimal, does it handle edge cases?).\n");
        promptBuilder.append("Return the evaluation as raw JSON matching this output schema exactly:\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"total_score\": 85, // out of 100\n");
        promptBuilder.append("  \"evaluations\": [\n");
        promptBuilder.append("    {\n");
        promptBuilder.append("      \"question\": \"<the question text>\",\n");
        promptBuilder.append("      \"feedback\": \"<your brief feedback on their approach and code>\",\n");
        promptBuilder.append("      \"approach_score\": 3,\n");
        promptBuilder.append("      \"code_score\": 5,\n");
        promptBuilder.append("      \"score\": 8 // sum of approach_score and code_score\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("    // 10 items total\n");
        promptBuilder.append("  ]\n");
        promptBuilder.append("}\n\n");
        promptBuilder.append("Return ONLY raw JSON, no markdown blocks.");

        String llmResponse = llmService.generate(promptBuilder.toString());
        llmResponse = llmResponse.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<Map<String, Object>>() {});
            
            // Save to DB for the user history
            Interview interview = Interview.builder()
                    .user(user)
                    .topic(request.getTopic() + " - " + request.getCategory() + " (Manipulation)")
                    .roundType(Interview.RoundType.CODING) // Or generic CODING since MANIPULATION doesn't exist
                    .status(Interview.Status.COMPLETED)
                    .currentQuestionIndex(request.getQuestions().size())
                    .build();
            interviewRepository.save(interview);

            int totalScore = (Integer) response.getOrDefault("total_score", 0);
            
            // We just store one overarching Evaluation representing the total test
            Question summaryQuestion = Question.builder()
                    .interview(interview)
                    .text("Manipulation Challenge: " + request.getCategory())
                    .difficulty(Question.Difficulty.EASY)
                    .order(1)
                    .build();
            questionRepository.save(summaryQuestion);

            Answer summaryAnswer = Answer.builder()
                    .question(summaryQuestion)
                    .userInput("Approaches: " + request.getApproaches() + "\nAnswers: " + request.getAnswers())
                    .evaluationStatus(Answer.EvaluationStatus.COMPLETED)
                    .build();
            answerRepository.save(summaryAnswer);

            Evaluation evaluation = Evaluation.builder()
                    .answer(summaryAnswer)
                    .score(totalScore)
                    .strengths("Manipulation challenge completed.")
                    .weaknesses("")
                    .improvements(llmResponse) // raw json stored as improvements
                    .build();
            evaluationRepository.save(evaluation);

            return response;
        } catch (Exception e) {
            log.error("Failed to parse LLM response for manipulation evaluation", e);
            throw new RuntimeException("Failed to evaluate manipulation answers.");
        }
    }

    public com.asknehru.interviewsimulator.test.category.SavedQuestionSet saveQuestionSet(com.asknehru.interviewsimulator.test.category.SavedQuestionSet request) {
        return savedQuestionSetRepository.save(request);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.asknehru.interviewsimulator.test.category.SavedQuestionSet> getSavedQuestionSets(String topic, String category) {
        return savedQuestionSetRepository.findByTopicAndCategory(topic, category);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> getManipulationHistory(User user) {
        List<Interview> interviews = interviewRepository.findByUserOrderByUpdatedAtDesc(user);
        List<Map<String, Object>> history = new ArrayList<>();

        for (Interview interview : interviews) {
            if (interview.getTopic() != null && interview.getTopic().contains("(Manipulation)")) {
                try {
                    List<Question> questions = questionRepository.findByInterviewOrderByOrderAscCreatedAtAsc(interview);
                    if (questions.isEmpty()) continue;
                    Question question = questions.get(0);
                    
                    Answer answer = answerRepository.findTopByQuestionOrderByIdDesc(question).orElse(null);
                    if (answer != null) {
                        Evaluation evaluation = evaluationRepository.findByAnswer(answer);
                        if (evaluation != null && evaluation.getImprovements() != null) {
                            JsonNode evalNode = objectMapper.readTree(evaluation.getImprovements());
                            
                            List<String> questionsList = new ArrayList<>();
                            if (evalNode.has("evaluations")) {
                                for (JsonNode ev : evalNode.get("evaluations")) {
                                    if (ev.has("question")) {
                                        questionsList.add(ev.get("question").asText());
                                    }
                                }
                            }
                            
                            Map<String, Object> record = new HashMap<>();
                            record.put("id", interview.getId());
                            // Clean up topic string to make it display-friendly, e.g. "Java - String Manipulation (Manipulation)" -> "Java - String Manipulation"
                            String cleanTopic = interview.getTopic().replace(" (Manipulation)", "");
                            record.put("topic", cleanTopic);
                            record.put("score", evaluation.getScore());
                            record.put("date", interview.getUpdatedAt());
                            record.put("questions", questionsList);
                            
                            history.add(record);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to parse history for interview {}", interview.getId(), e);
                }
            }
        }
        return history;
    }
}
