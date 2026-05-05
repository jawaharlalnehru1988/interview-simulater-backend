package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.dto.GeneratedQuestion;
import com.asknehru.interviewsimulator.model.*;
import com.asknehru.interviewsimulator.repository.AnswerRepository;
import com.asknehru.interviewsimulator.repository.EvaluationRepository;
import com.asknehru.interviewsimulator.repository.InterviewRepository;
import com.asknehru.interviewsimulator.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationRepository evaluationRepository;
    private final QuestionGeneratorService questionGenerator;
    private final EvaluatorService evaluatorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${interview.max-questions:10}")
    private int maxQuestions;

    @Transactional
    public Interview startInterview(User user, String topic, Interview.RoundType roundType) {
        Interview interview = Interview.builder()
                .user(user)
                .topic(topic)
                .roundType(roundType)
                .status(Interview.Status.IN_PROGRESS)
                .currentQuestionIndex(0)
                .build();
        return interviewRepository.save(interview);
    }

    private int getMaxQuestions(Interview.RoundType roundType) {
        if (roundType == Interview.RoundType.MCQ) return 25;
        if (roundType == Interview.RoundType.BASIC) return 10;
        if (roundType == Interview.RoundType.CRITICAL_SCENARIO) return 5;
        if (roundType == Interview.RoundType.CODING) return 5;
        return maxQuestions;
    }

    @Transactional
    public Question generateNextQuestion(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));

        if (interview.getStatus() == Interview.Status.COMPLETED) {
            throw new RuntimeException("Interview already completed");
        }

        Question.Difficulty nextDifficulty = resolveNextDifficulty(interview);
        List<Question> prevQuestions = questionRepository.findByInterviewOrderByOrderDescCreatedAtDesc(interview);
        GeneratedQuestion generated = questionGenerator.generate(interview.getTopic(), nextDifficulty, interview.getRoundType(), prevQuestions);

        String mcqOptionsJson = "";
        try {
            mcqOptionsJson = objectMapper.writeValueAsString(generated.getMcqOptions());
        } catch (JsonProcessingException e) {
            // Handle error
        }

        Question question = Question.builder()
                .interview(interview)
                .text(generated.getQuestion())
                .suggestedAnswer(generated.getSuggestedAnswer())
                .mcqOptions(mcqOptionsJson)
                .difficulty(nextDifficulty)
                .order(interview.getCurrentQuestionIndex() + 1)
                .build();

        interview.setCurrentQuestionIndex(interview.getCurrentQuestionIndex() + 1);
        if (interview.getCurrentQuestionIndex() >= getMaxQuestions(interview.getRoundType())) {
            interview.setStatus(Interview.Status.COMPLETED);
        }
        interviewRepository.save(interview);

        return questionRepository.save(question);
    }

    @Transactional
    public Map<String, Object> submitAnswer(Long questionId, String userInput) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Answer answer = Answer.builder()
                .question(question)
                .userInput(userInput)
                .evaluationStatus(Answer.EvaluationStatus.PENDING)
                .build();
        answer = answerRepository.save(answer);

        // Synchronous evaluation (can be async later)
        Map<String, Object> immutableEvalResult = evaluatorService.evaluate(question.getText(), userInput, question.getInterview().getRoundType());
        Map<String, Object> evaluationResult = new java.util.HashMap<>(immutableEvalResult);
        
        int rawScore = (Integer) evaluationResult.get("score");
        int finalScore = rawScore;
        
        if (question.getInterview().getRoundType() == Interview.RoundType.MCQ) {
            finalScore = rawScore >= 70 ? 1 : 0;
        } else if (question.getInterview().getRoundType() == Interview.RoundType.BASIC || question.getInterview().getRoundType() == Interview.RoundType.CRITICAL_SCENARIO) {
            finalScore = (int) Math.round((double) rawScore / 10.0);
        } else if (question.getInterview().getRoundType() == Interview.RoundType.CODING) {
            finalScore = (int) Math.round((double) rawScore / 5.0);
        }

        Evaluation evaluation = Evaluation.builder()
                .answer(answer)
                .score(finalScore)
                .strengths(evaluationResult.get("strengths").toString())
                .weaknesses(evaluationResult.get("weaknesses").toString())
                .improvements(evaluationResult.get("improvements").toString())
                .build();
        evaluationRepository.save(evaluation);
        
        // Update evaluationResult to return the scaled score to the frontend
        evaluationResult.put("score", finalScore);

        answer.setEvaluationStatus(Answer.EvaluationStatus.COMPLETED);
        answerRepository.save(answer);

        maybeCompleteInterview(question.getInterview());

        return evaluationResult;
    }

    private Question.Difficulty resolveNextDifficulty(Interview interview) {
        // Get all questions for this interview ordered by 'order' descending
        List<Question> questions = questionRepository.findByInterviewOrderByOrderDescCreatedAtDesc(interview);
        
        for (Question q : questions) {
            Optional<Answer> answerOpt = answerRepository.findTopByQuestionOrderByIdDesc(q);
            if (answerOpt.isPresent() && answerOpt.get().getEvaluation() != null) {
                Integer score = answerOpt.get().getEvaluation().getScore();
                if (score >= 80) return Question.Difficulty.HARD;
                if (score >= 60) return Question.Difficulty.MEDIUM;
                return Question.Difficulty.EASY;
            }
        }
        
        return Question.Difficulty.EASY;
    }

    private void maybeCompleteInterview(Interview interview) {
        if (interview.getCurrentQuestionIndex() >= getMaxQuestions(interview.getRoundType())) {
            interview.setStatus(Interview.Status.COMPLETED);
            interviewRepository.save(interview);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(User user) {
        List<Interview> interviews = interviewRepository.findByUserOrderByUpdatedAtDesc(user);
        return interviews.stream().map(this::buildInterviewSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
        return buildInterviewSummary(interview);
    }

    private Map<String, Object> buildInterviewSummary(Interview interview) {
        List<Question> questions = questionRepository.findByInterviewOrderByOrderDescCreatedAtDesc(interview);
        
        // Reverse to show in chronological order for the summary if we want, or keep as is.
        // The frontend sorts it or expects order. Let's just sort by order ascending.
        questions.sort((q1, q2) -> {
            int order1 = q1.getOrder() != null ? q1.getOrder() : 0;
            int order2 = q2.getOrder() != null ? q2.getOrder() : 0;
            return Integer.compare(order1, order2);
        });

        int totalScore = 0;
        int evaluatedQuestions = 0;

        List<Map<String, Object>> mappedQuestions = questions.stream().map(q -> {
            Optional<Answer> answerOpt = answerRepository.findTopByQuestionOrderByIdDesc(q);
            Map<String, Object> qMap = new java.util.HashMap<>();
            qMap.put("question_id", q.getId());
            qMap.put("order", q.getOrder());
            qMap.put("difficulty", q.getDifficulty().toString());
            qMap.put("question", q.getText());
            
            if (answerOpt.isPresent()) {
                Answer answer = answerOpt.get();
                qMap.put("answer", answer.getUserInput());
                qMap.put("evaluation_status", answer.getEvaluationStatus().toString());
                if (answer.getEvaluation() != null) {
                    qMap.put("score", answer.getEvaluation().getScore());
                } else {
                    qMap.put("score", null);
                }
            } else {
                qMap.put("answer", null);
                qMap.put("evaluation_status", null);
                qMap.put("score", null);
            }
            return qMap;
        }).toList();

        for (Map<String, Object> q : mappedQuestions) {
            if (q.get("score") != null) {
                totalScore += (Integer) q.get("score");
                evaluatedQuestions++;
            }
        }

        Integer averageScore = evaluatedQuestions > 0 ? totalScore / evaluatedQuestions : null;

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("interview_id", interview.getId());
        summary.put("topic", interview.getTopic());
        summary.put("round", interview.getRoundType().toString());
        summary.put("status", interview.getStatus().toString());
        summary.put("questions_asked", questions.size());
        summary.put("evaluations_completed", evaluatedQuestions);
        summary.put("average_score", averageScore);
        summary.put("questions", mappedQuestions);
        summary.put("updated_at", interview.getUpdatedAt());
        
        return summary;
    }
}
