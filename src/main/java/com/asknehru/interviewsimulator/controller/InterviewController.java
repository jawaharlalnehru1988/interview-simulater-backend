package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.dto.StartInterviewRequest;
import com.asknehru.interviewsimulator.dto.SubmitAnswerRequest;
import com.asknehru.interviewsimulator.model.Interview;
import com.asknehru.interviewsimulator.model.Question;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.InterviewRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @PostMapping("/start/")
    public ResponseEntity<?> startInterview(@RequestBody StartInterviewRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Interview interview = interviewService.startInterview(user, request.getTopic(), request.getRoundType());
        return ResponseEntity.status(201).body(Map.of("interview_id", interview.getId()));
    }

    @GetMapping("/{interviewId}/next/")
    public ResponseEntity<?> nextQuestion(@PathVariable Long interviewId) {
        Question question = interviewService.generateNextQuestion(interviewId);
        
        List<String> options = List.of();
        if (question.getMcqOptions() != null && !question.getMcqOptions().isEmpty()) {
            try {
                options = objectMapper.readValue(question.getMcqOptions(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception e) {
                // Log error
            }
        }

        return ResponseEntity.ok(Map.of(
                "interview_id", interviewId,
                "question_id", question.getId(),
                "question", question.getText(),
                "difficulty", question.getDifficulty(),
                "question_number", question.getOrder(),
                "status", "in_progress",
                "suggested_answer", question.getSuggestedAnswer() != null ? question.getSuggestedAnswer() : "",
                "mcq_options", options
        ));
    }

    @PostMapping("/answer/")
    public ResponseEntity<?> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        Map<String, Object> evaluation = interviewService.submitAnswer(request.getQuestionId(), request.getAnswer());
        return ResponseEntity.ok(Map.of(
                "question_id", request.getQuestionId(),
                "evaluation_status", "completed",
                "evaluation", evaluation
        ));
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<Map<String, Object>> history = interviewService.getHistory(user);
        return ResponseEntity.ok(Map.of("interviews", history, "count", history.size()));
    }

    @GetMapping("/{interviewId}/summary/")
    public ResponseEntity<?> getSummary(@PathVariable Long interviewId) {
        try {
            Map<String, Object> summary = interviewService.getSummary(interviewId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
