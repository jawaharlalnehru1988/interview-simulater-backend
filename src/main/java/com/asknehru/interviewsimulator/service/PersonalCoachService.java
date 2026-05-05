package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.PersonalCoachAttempt;
import com.asknehru.interviewsimulator.model.PersonalCoachSession;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.PersonalCoachAttemptRepository;
import com.asknehru.interviewsimulator.repository.PersonalCoachSessionRepository;
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
public class PersonalCoachService {

    private final LlmService llmService;
    private final PersonalCoachSessionRepository sessionRepository;
    private final PersonalCoachAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> startOrResumeSession(User user, String topic) {
        PersonalCoachSession session = sessionRepository.findFirstByUserAndTopicOrderByIdDesc(user, topic)
                .orElseGet(() -> {
                    List<String> subtopics = generateSubtopics(topic);
                    PersonalCoachSession s = PersonalCoachSession.builder()
                            .user(user)
                            .topic(topic)
                            .subtopics(serialize(subtopics))
                            .stage(PersonalCoachSession.Stage.SUBTOPIC_SELECTION)
                            .build();
                    return sessionRepository.save(s);
                });
        
        Map<String, Object> response = new HashMap<>();
        response.put("session_id", session.getId());
        response.put("topic", session.getTopic());
        response.put("subtopics", deserializeList(session.getSubtopics()));
        response.put("stage", session.getStage() != null ? session.getStage().name() : "SUBTOPIC_SELECTION");
        response.put("coach_prompt", "Choose one subtopic to begin learning.");
        return response;
    }

    public PersonalCoachSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Session not found"));
    }

    public Map<String, Object> getSessionData(Long sessionId) {
        PersonalCoachSession session = getSession(sessionId);
        Map<String, Object> response = new HashMap<>();
        response.put("session_id", session.getId());
        response.put("topic", session.getTopic());
        response.put("subtopics", deserializeList(session.getSubtopics()));
        response.put("lessons_by_subtopic", deserializeMap(session.getLessonsBySubtopic()));
        response.put("selected_subtopic", session.getSelectedSubtopic());
        response.put("selected_lesson", session.getSelectedLesson());
        response.put("stage", session.getStage() != null ? session.getStage().name() : "");
        response.put("lesson", session.getCurrentLesson());
        response.put("question", session.getCurrentQuestion());
        response.put("attempt_count", session.getAttemptCount() != null ? session.getAttemptCount() : 0);
        response.put("coach_prompt", "Resume your learning journey.");
        
        if (session.getSelectedSubtopic() != null) {
            Map<String, List<String>> map = deserializeMap(session.getLessonsBySubtopic());
            response.put("available_lessons", map.getOrDefault(session.getSelectedSubtopic(), new ArrayList<>()));
        }

        List<PersonalCoachAttempt> attempts = attemptRepository.findBySessionOrderByCreatedAtDesc(session);
        List<String> practicedSubtopics = attempts.stream()
            .map(PersonalCoachAttempt::getSubtopic)
            .filter(s -> s != null && !s.isEmpty())
            .distinct().toList();
        response.put("practiced_subtopics", practicedSubtopics);
        
        if (session.getSelectedSubtopic() != null) {
            List<String> practicedLessons = attempts.stream()
                .filter(a -> session.getSelectedSubtopic().equals(a.getSubtopic()))
                .map(PersonalCoachAttempt::getLesson)
                .filter(l -> l != null && !l.isEmpty())
                .distinct().toList();
            response.put("practiced_lessons", practicedLessons);
        }

        attempts.stream().max(Comparator.comparing(PersonalCoachAttempt::getId)).ifPresent(latest -> {
            Map<String, Object> lat = new HashMap<>();
            lat.put("score", latest.getScore());
            lat.put("strengths", deserializeList(latest.getStrengths()));
            lat.put("gaps", deserializeList(latest.getGaps()));
            lat.put("feedback", latest.getFeedback());
            lat.put("coach_decision", latest.getCoachDecision().name());
            response.put("latest_attempt", lat);
        });

        Map<String, List<String>> practicedLessonsMap = new HashMap<>();
        for (PersonalCoachAttempt attempt : attempts) {
            if (attempt.getSubtopic() != null && attempt.getLesson() != null) {
                practicedLessonsMap.computeIfAbsent(attempt.getSubtopic(), k -> new ArrayList<>())
                                   .add(attempt.getLesson());
            }
        }
        practicedLessonsMap.replaceAll((k, v) -> v.stream().distinct().toList());
        response.put("practiced_lessons_map", practicedLessonsMap);

        return response;
    }

    public List<String> generateSubtopics(String topic) {
        String prompt = String.format(
            "You are a personal interview coach.\n" +
            "Generate a clean list of subtopics for topic: %s\n\n" +
            "Rules:\n" +
            "- Return only subtopic names.\n" +
            "- Count can vary (typically 6 to 20).\n" +
            "- Do not number lines or add explanations.\n",
            topic
        );

        String raw = llmService.generate(prompt);
        List<String> lines = extractLines(raw);
        if (lines.size() >= 4) return lines;

        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
            "Failed to generate subtopics from AI. Please check your API key and connection."
        );
    }

    @Transactional
    public Map<String, Object> chooseSubtopic(Long sessionId, String subtopic) {
        PersonalCoachSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setSelectedSubtopic(subtopic);
        
        List<String> lessons = generateLessons(session.getTopic(), subtopic);
        Map<String, List<String>> lessonsMap = deserializeMap(session.getLessonsBySubtopic());
        lessonsMap.put(subtopic, lessons);
        
        session.setLessonsBySubtopic(serialize(lessonsMap));
        session.setStage(PersonalCoachSession.Stage.LESSON_SELECTION);
        sessionRepository.save(session);
        
        return Map.of(
            "session_id", session.getId(),
            "topic", session.getTopic(),
            "subtopic", subtopic,
            "lessons", lessons,
            "practiced_subtopics", attemptRepository.findBySessionOrderByCreatedAtDesc(session).stream()
                    .map(PersonalCoachAttempt::getSubtopic)
                    .filter(s -> s != null && !s.isEmpty())
                    .distinct()
                    .toList(),
            "practiced_lessons", attemptRepository.findBySessionOrderByCreatedAtDesc(session).stream()
                    .filter(a -> subtopic.equals(a.getSubtopic()))
                    .map(PersonalCoachAttempt::getLesson)
                    .filter(l -> l != null && !l.isEmpty())
                    .distinct()
                    .toList(),
            "stage", session.getStage().name(),
            "coach_prompt", "You are now learning '" + subtopic + "'. Please select a specific lesson below to continue."
        );
    }

    public List<String> generateLessons(String topic, String subtopic) {
        String prompt = String.format(
            "You are a personal coach creating lesson modules.\n" +
            "Topic: %s, Subtopic: %s\n\n" +
            "Return STRICT JSON only:\n" +
            "{\"lessons\": [\"Lesson title 1\", \"Lesson title 2\"]}",
            topic, subtopic
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);
        List<String> lessons = normalizeList(parsed.path("lessons"));
        if (lessons.size() >= 3) return lessons;

        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
            "Failed to generate lessons from AI. Please check your API key and connection."
        );
    }

    @Transactional
    public Map<String, Object> chooseLesson(Long sessionId, String lesson) {
        PersonalCoachSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setSelectedLesson(lesson);

        String prompt = String.format(
            "Topic: %s, Subtopic: %s, Lesson: %s\n\n" +
            "Return STRICT JSON:\n" +
            "{\"lesson\": \"Markdown content paragraphs\", \"question\": \"one practice question\"}",
            session.getTopic(), session.getSelectedSubtopic(), lesson
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);
        
        if (!parsed.has("lesson") || !parsed.has("question")) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to generate lesson content from AI."
            );
        }

        String lessonText = parsed.path("lesson").asText();
        String question = parsed.path("question").asText();

        session.setCurrentLesson(lessonText);
        session.setCurrentQuestion(question);
        session.setStage(PersonalCoachSession.Stage.QUESTIONING);
        sessionRepository.save(session);

        return Map.of("lesson", lessonText, "question", question);
    }

    @Transactional
    public Map<String, Object> evaluateAnswer(Long sessionId, String answerText) {
        PersonalCoachSession session = sessionRepository.findById(sessionId).orElseThrow();
        
        String prompt = String.format(
            "Topic: %s, Subtopic: %s, Question: %s, Answer: %s\n\n" +
            "Return STRICT JSON: {\"score\": 0-100, \"strengths\": [], \"gaps\": [], \"feedback\": \"\", \"coach_decision\": \"advance|remediate\"}",
            session.getTopic(), session.getSelectedSubtopic(), session.getCurrentQuestion(), answerText
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        if (!parsed.has("score") || !parsed.has("coach_decision")) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "Failed to generate evaluation from AI. Please check your API key and connection."
            );
        }

        int score = parsed.path("score").asInt();
        String decision = parsed.path("coach_decision").asText().toUpperCase();
        PersonalCoachAttempt.CoachDecision coachDecision = decision.equals("ADVANCE") ? 
                PersonalCoachAttempt.CoachDecision.ADVANCE : PersonalCoachAttempt.CoachDecision.REMEDIATE;

        PersonalCoachAttempt attempt = PersonalCoachAttempt.builder()
                .session(session)
                .subtopic(session.getSelectedSubtopic())
                .lesson(session.getSelectedLesson())
                .question(session.getCurrentQuestion())
                .userAnswer(answerText)
                .score(score)
                .strengths(serialize(normalizeList(parsed.path("strengths"))))
                .gaps(serialize(normalizeList(parsed.path("gaps"))))
                .feedback(parsed.path("feedback").asText())
                .coachDecision(coachDecision)
                .build();
        attemptRepository.save(attempt);

        int currentAttemptCount = session.getAttemptCount() != null ? session.getAttemptCount() : 0;
        session.setAttemptCount(currentAttemptCount + 1);
        
        int currentMastery = session.getMasteryScore() != null ? session.getMasteryScore() : score;
        session.setMasteryScore(currentAttemptCount == 0 ? score : (currentMastery + score) / 2);
        
        if (coachDecision == PersonalCoachAttempt.CoachDecision.ADVANCE) {
            session.setStage(PersonalCoachSession.Stage.LESSON_SELECTION);
        }
        sessionRepository.save(session);

        return Map.of(
            "score", score,
            "feedback", attempt.getFeedback(),
            "decision", coachDecision.name(),
            "strengths", normalizeList(parsed.path("strengths")),
            "gaps", normalizeList(parsed.path("gaps"))
        );
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> normalizeList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) list.add(n.asText());
        }
        return list;
    }

    private Map<String, List<String>> deserializeMap(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> extractLines(String raw) {
        if (raw == null) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            String cleaned = line.replaceAll("^[-*\\d\\.)\\s]+", "").trim();
            if (!cleaned.isEmpty()) lines.add(cleaned);
        }
        return lines;
    }
}
