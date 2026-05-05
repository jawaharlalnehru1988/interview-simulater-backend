package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.*;
import com.asknehru.interviewsimulator.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class HRVoiceInterviewService {

    private final LlmService llmService;
    private final HRVoiceInterviewSessionRepository sessionRepository;
    private final HRVoiceInterviewTurnRepository turnRepository;
    private final CandidateProfileRepository profileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public HRVoiceInterviewSession startSession(User user, CandidateProfile profile, 
                                               UserAspiration aspiration, JobDescriptionAnalysis jobAnalysis) {
        
        List<String> questions = generateQuestions(profile, aspiration, jobAnalysis);
        
        HRVoiceInterviewSession session = HRVoiceInterviewSession.builder()
                .user(user)
                .profile(profile)
                .aspiration(aspiration)
                .jobAnalysis(jobAnalysis)
                .questions(serialize(questions))
                .currentQuestionIndex(0)
                .status(HRVoiceInterviewSession.Status.IN_PROGRESS)
                .build();
        
        return sessionRepository.save(session);
    }

    public List<String> generateQuestions(CandidateProfile profile, UserAspiration aspiration, 
                                         JobDescriptionAnalysis jobAnalysis) {
        String prompt = String.format(
            "You are an HR recruiter conducting an initial screening call.\n" +
            "Generate 12 short, realistic HR screening questions.\n\n" +
            "Candidate Profile: %s\nAspiration: %s\nJD Context: %s\n\n" +
            "Rules:\n- Focus on HR/recruiter screening style.\n- Keep each question on one line.\n- No numbering.",
            serialize(profile), serialize(aspiration), serialize(jobAnalysis)
        );

        String raw = llmService.generate(prompt);
        return extractLines(raw);
    }

    @Transactional
    public Map<String, Object> submitTurn(Long sessionId, String answer) {
        HRVoiceInterviewSession session = sessionRepository.findById(sessionId).orElseThrow();
        List<String> questions = deserializeList(session.getQuestions());
        
        if (session.getCurrentQuestionIndex() >= questions.size()) {
            throw new RuntimeException("Interview already finished");
        }

        String question = questions.get(session.getCurrentQuestionIndex());
        
        String prompt = String.format(
            "Question: %s\nAnswer: %s\n\nReturn STRICT JSON: {\"score\": 0-100, \"strengths\": [], \"weaknesses\": [], \"improvements\": [], \"better_answer\": \"\"}",
            question, answer
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        if (!parsed.has("score")) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to generate turn evaluation from AI."
            );
        }

        HRVoiceInterviewTurn turn = HRVoiceInterviewTurn.builder()
                .session(session)
                .question(question)
                .answer(answer)
                .score(parsed.path("score").asInt())
                .strengths(serialize(normalizeList(parsed.path("strengths"))))
                .weaknesses(serialize(normalizeList(parsed.path("weaknesses"))))
                .improvements(serialize(normalizeList(parsed.path("improvements"))))
                .build();
        turnRepository.save(turn);

        session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
        if (session.getCurrentQuestionIndex() >= questions.size()) {
            finalizeInterview(session);
        }
        sessionRepository.save(session);

        if (!parsed.has("better_answer") || parsed.path("improvements").isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to generate feedback from AI."
            );
        }

        return Map.of(
            "score", turn.getScore(),
            "better_answer", parsed.path("better_answer").asText(),
            "feedback", parsed.path("improvements").path(0).asText()
        );
    }

    private void finalizeInterview(HRVoiceInterviewSession session) {
        List<HRVoiceInterviewTurn> turns = turnRepository.findBySessionOrderByCreatedAtAsc(session);
        double avgScore = turns.stream().mapToInt(HRVoiceInterviewTurn::getScore).average().orElse(0);
        
        session.setStatus(HRVoiceInterviewSession.Status.COMPLETED);
        session.setPassDecision(avgScore >= 65);
        
        // Final feedback logic simplified
        session.setFinalFeedback(serialize(Map.of(
            "average_score", avgScore,
            "overall_feedback", "Interview completed. Review individual turns for feedback."
        )));
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> extractLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null) return lines;
        for (String line : raw.split("\\n")) {
            String cleaned = line.replaceAll("^[-*\\d\\.)\\s]+", "").trim();
            if (!cleaned.isEmpty()) lines.add(cleaned);
        }
        return lines;
    }

    private List<String> normalizeList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) list.add(n.asText());
        }
        return list;
    }
}
