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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalCoachService {

    private final LlmService llmService;
    private final PersonalCoachSessionRepository sessionRepository;
    private final PersonalCoachAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PersonalCoachSession startOrResumeSession(User user, String topic) {
        return sessionRepository.findByUserAndTopic(user, topic)
                .orElseGet(() -> {
                    List<String> subtopics = generateSubtopics(topic);
                    PersonalCoachSession session = PersonalCoachSession.builder()
                            .user(user)
                            .topic(topic)
                            .subtopics(serialize(subtopics))
                            .stage(PersonalCoachSession.Stage.SUBTOPIC_SELECTION)
                            .build();
                    return sessionRepository.save(session);
                });
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

        // Fallback or retry with JSON
        return fallbackSubtopics(topic);
    }

    @Transactional
    public PersonalCoachSession chooseSubtopic(Long sessionId, String subtopic) {
        PersonalCoachSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setSelectedSubtopic(subtopic);
        
        List<String> lessons = generateLessons(session.getTopic(), subtopic);
        Map<String, List<String>> lessonsMap = deserializeMap(session.getLessonsBySubtopic());
        lessonsMap.put(subtopic, lessons);
        
        session.setLessonsBySubtopic(serialize(lessonsMap));
        session.setStage(PersonalCoachSession.Stage.LESSON_SELECTION);
        return sessionRepository.save(session);
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

        return List.of("Introduction to " + subtopic, "Core Concepts", "Practical Usage", "Interview Practice");
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
        
        String lessonText = parsed.path("lesson").asText("Lesson content loading error.");
        String question = parsed.path("question").asText("Describe how you apply " + lesson);

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

        int score = parsed.path("score").asInt(50);
        String decision = parsed.path("coach_decision").asText("remediate").toUpperCase();
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
                .feedback(parsed.path("feedback").asText(""))
                .coachDecision(coachDecision)
                .build();
        attemptRepository.save(attempt);

        session.setAttemptCount(session.getAttemptCount() + 1);
        session.setMasteryScore((session.getMasteryScore() + score) / 2);
        
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

    private List<String> extractLines(String raw) {
        if (raw == null) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            String cleaned = line.replaceAll("^[-*\\d\\.)\\s]+", "").trim();
            if (!cleaned.isEmpty()) lines.add(cleaned);
        }
        return lines;
    }

    private List<String> fallbackSubtopics(String topic) {
        return List.of("Fundamentals of " + topic, "Advanced " + topic, "Common Interview Questions on " + topic, "Practical Applications");
    }
}
