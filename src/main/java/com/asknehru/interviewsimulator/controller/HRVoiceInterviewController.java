package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.*;
import com.asknehru.interviewsimulator.repository.*;
import com.asknehru.interviewsimulator.service.HRVoiceInterviewService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/hr-voice")
@RequiredArgsConstructor
public class HRVoiceInterviewController {

    private final HRVoiceInterviewService hrService;
    private final HRVoiceInterviewSessionRepository sessionRepository;
    private final HRVoiceInterviewTurnRepository turnRepository;
    private final CandidateProfileRepository profileRepository;
    private final UserAspirationRepository aspirationRepository;
    private final JobDescriptionAnalysisRepository jdRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> toSessionMap(HRVoiceInterviewSession session) {
        List<String> questionsList = List.of();
        try {
            if (session.getQuestions() != null && !session.getQuestions().isEmpty()) {
                questionsList = objectMapper.readValue(session.getQuestions(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            // ignore
        }

        String currentQuestion = "";
        if (session.getCurrentQuestionIndex() < questionsList.size()) {
            currentQuestion = questionsList.get(session.getCurrentQuestionIndex());
        }

        Map<String, Object> map = new java.util.HashMap<>();
        map.put("session_id", session.getId());
        map.put("status", session.getStatus() != null ? session.getStatus().name() : "IN_PROGRESS");
        map.put("question_count", questionsList.size());
        map.put("current_question_index", session.getCurrentQuestionIndex());
        map.put("current_question", currentQuestion);

        // Build context
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("target_job", session.getAspiration() != null ? session.getAspiration().getTargetJob() : "");
        context.put("company_name", session.getJobAnalysis() != null ? session.getJobAnalysis().getCompanyName() : "");

        Map<String, Object> profileMap = new java.util.HashMap<>();
        CandidateProfile p = session.getProfile();
        if (p != null) {
            profileMap.put("current_position", p.getCurrentPosition() != null ? p.getCurrentPosition() : "");
            profileMap.put("current_company", p.getCurrentCompany() != null ? p.getCurrentCompany() : "");
            profileMap.put("total_experience_years", p.getTotalExperienceYears());
            
            List<String> skills = List.of();
            try {
                if (p.getPrimarySkills() != null && !p.getPrimarySkills().isEmpty()) {
                    skills = objectMapper.readValue(p.getPrimarySkills(), new TypeReference<List<String>>() {});
                }
            } catch (Exception e) {}
            profileMap.put("primary_skills", skills);
            profileMap.put("salary_expectation", p.getSalaryExpectation() != null ? p.getSalaryExpectation() : "");
            profileMap.put("notice_period", p.getNoticePeriod() != null ? p.getNoticePeriod() : "");
            profileMap.put("preferred_role", p.getPreferredRole() != null ? p.getPreferredRole() : "");
            profileMap.put("is_profile_complete", true);
        } else {
            profileMap.put("current_position", "");
            profileMap.put("current_company", "");
            profileMap.put("total_experience_years", null);
            profileMap.put("primary_skills", List.of());
            profileMap.put("salary_expectation", "");
            profileMap.put("notice_period", "");
            profileMap.put("preferred_role", "");
            profileMap.put("is_profile_complete", false);
        }
        context.put("profile", profileMap);
        map.put("context", context);

        return map;
    }

    @PostMapping("/start/")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        CandidateProfile profile = profileRepository.findByUser(user).orElse(null);
        
        Long aspId = null;
        if (request.containsKey("aspirationId")) {
            aspId = ((Number) request.get("aspirationId")).longValue();
        } else if (request.containsKey("aspiration_id") && request.get("aspiration_id") != null) {
            aspId = ((Number) request.get("aspiration_id")).longValue();
        }
        
        Long jdId = null;
        if (request.containsKey("jobAnalysisId")) {
            jdId = ((Number) request.get("jobAnalysisId")).longValue();
        } else if (request.containsKey("jd_analysis_id") && request.get("jd_analysis_id") != null) {
            jdId = ((Number) request.get("jd_analysis_id")).longValue();
        }

        UserAspiration aspiration = aspId != null ? aspirationRepository.findById(aspId).orElse(null) : null;
        JobDescriptionAnalysis jobAnalysis = jdId != null ? jdRepository.findById(jdId).orElse(null) : null;

        HRVoiceInterviewSession session = hrService.startSession(user, profile, aspiration, jobAnalysis);
        return ResponseEntity.ok(toSessionMap(session));
    }

    @PostMapping("/{sessionId}/answer/")
    public ResponseEntity<?> submitAnswer(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        String answer = request.get("answer");
        
        HRVoiceInterviewSession session = sessionRepository.findById(sessionId).orElseThrow();
        
        hrService.submitTurn(sessionId, answer);
        
        HRVoiceInterviewSession updatedSession = sessionRepository.findById(sessionId).orElseThrow();
        List<HRVoiceInterviewTurn> turns = turnRepository.findBySessionOrderByCreatedAtAsc(updatedSession);
        HRVoiceInterviewTurn latestTurn = turns.get(turns.size() - 1);
        
        List<String> strengths = List.of();
        List<String> weaknesses = List.of();
        List<String> improvements = List.of();
        try {
            if (latestTurn.getStrengths() != null) {
                strengths = objectMapper.readValue(latestTurn.getStrengths(), new TypeReference<List<String>>() {});
            }
            if (latestTurn.getWeaknesses() != null) {
                weaknesses = objectMapper.readValue(latestTurn.getWeaknesses(), new TypeReference<List<String>>() {});
            }
            if (latestTurn.getImprovements() != null) {
                improvements = objectMapper.readValue(latestTurn.getImprovements(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {}

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("session_id", sessionId);
        response.put("status", updatedSession.getStatus().name());
        response.put("question_score", latestTurn.getScore());
        
        Map<String, Object> evaluation = new java.util.HashMap<>();
        evaluation.put("score", latestTurn.getScore());
        evaluation.put("strengths", strengths);
        evaluation.put("weaknesses", weaknesses);
        evaluation.put("improvements", improvements);
        response.put("evaluation", evaluation);

        List<String> questionsList = List.of();
        try {
            if (updatedSession.getQuestions() != null) {
                questionsList = objectMapper.readValue(updatedSession.getQuestions(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {}

        if (updatedSession.getStatus() == HRVoiceInterviewSession.Status.IN_PROGRESS) {
            response.put("next_question_index", updatedSession.getCurrentQuestionIndex());
            if (updatedSession.getCurrentQuestionIndex() < questionsList.size()) {
                response.put("next_question", questionsList.get(updatedSession.getCurrentQuestionIndex()));
            }
        } else {
            double avgScore = turns.stream().mapToInt(HRVoiceInterviewTurn::getScore).average().orElse(0);
            
            List<Map<String, Object>> strongAnswers = new ArrayList<>();
            List<Map<String, Object>> weakAnswers = new ArrayList<>();
            List<String> improvementPlan = new ArrayList<>();

            for (HRVoiceInterviewTurn t : turns) {
                List<String> tStrengths = List.of();
                List<String> tWeaknesses = List.of();
                List<String> tImprovements = List.of();
                try {
                    if (t.getStrengths() != null) tStrengths = objectMapper.readValue(t.getStrengths(), new TypeReference<List<String>>() {});
                    if (t.getWeaknesses() != null) tWeaknesses = objectMapper.readValue(t.getWeaknesses(), new TypeReference<List<String>>() {});
                    if (t.getImprovements() != null) tImprovements = objectMapper.readValue(t.getImprovements(), new TypeReference<List<String>>() {});
                } catch (Exception e) {}

                Map<String, Object> answerDetails = new java.util.HashMap<>();
                answerDetails.put("question", t.getQuestion());
                answerDetails.put("answer", t.getAnswer());
                answerDetails.put("score", t.getScore());

                if (t.getScore() >= 70) {
                    answerDetails.put("strengths", tStrengths);
                    strongAnswers.add(answerDetails);
                } else {
                    answerDetails.put("weaknesses", tWeaknesses);
                    answerDetails.put("improvements", tImprovements);
                    weakAnswers.add(answerDetails);
                    improvementPlan.addAll(tImprovements);
                }
            }

            Map<String, Object> finalFeedback = new java.util.HashMap<>();
            try {
                if (updatedSession.getFinalFeedback() != null) {
                    finalFeedback = objectMapper.readValue(updatedSession.getFinalFeedback(), new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception e) {}

            Map<String, Object> formattedFeedback = new java.util.HashMap<>();
            formattedFeedback.put("pass", updatedSession.getPassDecision() != null ? updatedSession.getPassDecision() : false);
            formattedFeedback.put("average_score", avgScore);
            formattedFeedback.put("overall_feedback", finalFeedback.getOrDefault("overall_feedback", "Interview completed. Review individual turns for feedback."));
            formattedFeedback.put("strong_answers", strongAnswers);
            formattedFeedback.put("weak_answers", weakAnswers);
            formattedFeedback.put("improvement_plan", improvementPlan.stream().distinct().toList());

            response.put("final_feedback", formattedFeedback);
            response.put("pass", updatedSession.getPassDecision() != null ? updatedSession.getPassDecision() : false);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<HRVoiceInterviewSession> history = sessionRepository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history.stream().map(this::toSessionMap).toList());
    }

    @GetMapping("/{id}/resume/")
    public ResponseEntity<?> resumeHRVoice(@PathVariable Long id) {
        HRVoiceInterviewSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Session not found"));
        
        List<HRVoiceInterviewTurn> turns = turnRepository.findBySessionOrderByCreatedAtAsc(session);
        
        List<Map<String, Object>> mappedTurns = turns.stream().map(t -> {
            List<String> strengths = List.of();
            List<String> weaknesses = List.of();
            List<String> improvements = List.of();
            try {
                if (t.getStrengths() != null) strengths = objectMapper.readValue(t.getStrengths(), new TypeReference<List<String>>() {});
                if (t.getWeaknesses() != null) weaknesses = objectMapper.readValue(t.getWeaknesses(), new TypeReference<List<String>>() {});
                if (t.getImprovements() != null) improvements = objectMapper.readValue(t.getImprovements(), new TypeReference<List<String>>() {});
            } catch (Exception e) {}

            Map<String, Object> tMap = new java.util.HashMap<>();
            tMap.put("question", t.getQuestion());
            tMap.put("answer", t.getAnswer());
            tMap.put("score", t.getScore());
            tMap.put("strengths", strengths);
            tMap.put("weaknesses", weaknesses);
            tMap.put("improvements", improvements);
            tMap.put("created_at", t.getCreatedAt().toString());
            return tMap;
        }).toList();

        Map<String, Object> map = toSessionMap(session);
        map.put("turns", mappedTurns);
        
        if (session.getStatus() == HRVoiceInterviewSession.Status.COMPLETED) {
            double avgScore = turns.stream().mapToInt(HRVoiceInterviewTurn::getScore).average().orElse(0);
            List<Map<String, Object>> strongAnswers = new ArrayList<>();
            List<Map<String, Object>> weakAnswers = new ArrayList<>();
            List<String> improvementPlan = new ArrayList<>();

            for (HRVoiceInterviewTurn t : turns) {
                List<String> tStrengths = List.of();
                List<String> tWeaknesses = List.of();
                List<String> tImprovements = List.of();
                try {
                    if (t.getStrengths() != null) tStrengths = objectMapper.readValue(t.getStrengths(), new TypeReference<List<String>>() {});
                    if (t.getWeaknesses() != null) tWeaknesses = objectMapper.readValue(t.getWeaknesses(), new TypeReference<List<String>>() {});
                    if (t.getImprovements() != null) tImprovements = objectMapper.readValue(t.getImprovements(), new TypeReference<List<String>>() {});
                } catch (Exception e) {}

                Map<String, Object> answerDetails = new java.util.HashMap<>();
                answerDetails.put("question", t.getQuestion());
                answerDetails.put("answer", t.getAnswer());
                answerDetails.put("score", t.getScore());

                if (t.getScore() >= 70) {
                    answerDetails.put("strengths", tStrengths);
                    strongAnswers.add(answerDetails);
                } else {
                    answerDetails.put("weaknesses", tWeaknesses);
                    answerDetails.put("improvements", tImprovements);
                    weakAnswers.add(answerDetails);
                    improvementPlan.addAll(tImprovements);
                }
            }

            Map<String, Object> formattedFeedback = new java.util.HashMap<>();
            formattedFeedback.put("pass", session.getPassDecision() != null ? session.getPassDecision() : false);
            formattedFeedback.put("average_score", avgScore);
            
            Map<String, Object> rawFeedback = Map.of();
            try {
                if (session.getFinalFeedback() != null) {
                    rawFeedback = objectMapper.readValue(session.getFinalFeedback(), new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception e) {}
            formattedFeedback.put("overall_feedback", rawFeedback.getOrDefault("overall_feedback", "Interview completed. Review individual turns for feedback."));
            formattedFeedback.put("strong_answers", strongAnswers);
            formattedFeedback.put("weak_answers", weakAnswers);
            formattedFeedback.put("improvement_plan", improvementPlan.stream().distinct().toList());

            map.put("final_feedback", formattedFeedback);
            map.put("pass", session.getPassDecision() != null ? session.getPassDecision() : false);
        } else {
            map.put("final_feedback", null);
            map.put("pass", null);
        }

        return ResponseEntity.ok(map);
    }

    @GetMapping("/{id}/")
    public ResponseEntity<?> getSession(@PathVariable Long id) {
        return resumeHRVoice(id);
    }
}
