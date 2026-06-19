package com.asknehru.interviewsimulator.syllabus;
import com.asknehru.interviewsimulator.ai.LlmService;

import com.asknehru.interviewsimulator.syllabus.Syllabus;
import com.asknehru.interviewsimulator.syllabus.SyllabusExplanation;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.syllabus.SyllabusExplanationRepository;
import com.asknehru.interviewsimulator.syllabus.SyllabusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyllabusService {

    private final LlmService llmService;
    private final SyllabusRepository syllabusRepository;
    private final SyllabusExplanationRepository explanationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Syllabus generateSyllabus(User user, String topic, String description, String sourceText) {
        String additionalInstructions = (description != null && !description.trim().isEmpty()) 
                ? "Additional Instructions from the user: " + description + "\n\n" 
                : "";

        String sourceTextInstruction = (sourceText != null && !sourceText.trim().isEmpty())
                ? "Generate a syllabus based strictly on the following source material:\n\"\"\"\n" + sourceText + "\n\"\"\"\n\n"
                : "";

        String prompt = String.format(
            "You are an expert technical instructor and curriculum designer.\n" +
            "%s" +
            "Create a highly structured and comprehensive learning syllabus for the topic: %s.\n\n" +
            "%s" +
            "Return STRICT JSON only, containing a list of objects representing the chapters/topics, each containing a list of subtopics. " +
            "Do NOT wrap it in any JSON key, just return a raw JSON list:\n" +
            "[\n" +
            "  {\n" +
            "    \"title\": \"Chapter 1: Title\",\n" +
            "    \"subtopics\": [\"Subtopic A\", \"Subtopic B\"]\n" +
            "  }\n" +
            "]",
            sourceTextInstruction, topic, additionalInstructions
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        // Fail fast if LLM fails
        if (parsed.isEmpty() || !parsed.isArray()) {
            throw new RuntimeException("Failed to generate syllabus from AI. Please try again.");
        }

        Syllabus syllabus = Syllabus.builder()
                .user(user)
                .topic(topic)
                .syllabusContent(serialize(parsed))
                .convertedToChecklist(false)
                .checklistContent("[]")
                .build();

        return syllabusRepository.save(syllabus);
    }

    @Transactional
    public Syllabus convertToChecklist(Long syllabusId, User user) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));

        if (syllabus.getConvertedToChecklist()) {
            return syllabus;
        }

        try {
            List<Map<String, Object>> checklistItems = new ArrayList<>();
            JsonNode syllabusNodes = objectMapper.readTree(syllabus.getSyllabusContent());
            
            int itemIndex = 0;
            if (syllabusNodes.isArray()) {
                for (int i = 0; i < syllabusNodes.size(); i++) {
                    JsonNode chapterNode = syllabusNodes.get(i);
                    String title = chapterNode.path("title").asText("");
                    JsonNode subtopicsNode = chapterNode.path("subtopics");
                    if (subtopicsNode.isArray()) {
                        for (int j = 0; j < subtopicsNode.size(); j++) {
                            String subtopic = subtopicsNode.get(j).asText("");
                            
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", "item-" + i + "-" + j);
                            item.put("topic", title);
                            item.put("subtopic", subtopic);
                            item.put("completed", false);
                            item.put("completedAt", null);
                            
                            checklistItems.add(item);
                        }
                    }
                }
            }

            syllabus.setConvertedToChecklist(true);
            syllabus.setChecklistContent(serialize(checklistItems));
            return syllabusRepository.save(syllabus);

        } catch (Exception e) {
            log.error("Failed to convert syllabus to checklist: {}", e.getMessage());
            throw new RuntimeException("Conversion failed");
        }
    }

    @Transactional
    public Syllabus toggleChecklistItem(Long syllabusId, User user, String itemId, boolean completed) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));

        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    syllabus.getChecklistContent(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            boolean found = false;
            for (Map<String, Object> item : items) {
                if (itemId.equals(item.get("id"))) {
                    item.put("completed", completed);
                    item.put("completedAt", completed ? LocalDateTime.now().toString() : null);
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new RuntimeException("Item not found in checklist");
            }

            syllabus.setChecklistContent(serialize(items));
            return syllabusRepository.save(syllabus);

        } catch (Exception e) {
            log.error("Failed to toggle checklist item: {}", e.getMessage());
            throw new RuntimeException("Toggle failed");
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional(readOnly = true)
    public String generateExplanation(User user, Long syllabusId, String topic, String subtopic) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));

        String prompt = String.format(
            "You are an expert technical tutor and software engineering instructor.\n" +
            "Provide a comprehensive, clear, and highly structured explanation of the following subtopic in the context of the main topic.\n\n" +
            "Main Topic: %s\n" +
            "Section/Chapter: %s\n" +
            "Subtopic: %s\n\n" +
            "Guidelines:\n" +
            "- Structure your response with a clear summary, key points, and coding examples/use cases where appropriate.\n" +
            "- Use clean Markdown (such as headers, lists, code snippets, and bold text) for formatting.\n" +
            "- Keep the tone professional, educational, and engaging.",
            syllabus.getTopic(), topic, subtopic
        );

        return llmService.generate(prompt);
    }

    @Transactional
    public SyllabusExplanation saveExplanation(User user, Long syllabusId, String topic, String subtopic, String explanation) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));

        SyllabusExplanation syllabusExplanation = explanationRepository
                .findByUserAndSyllabusAndTopicAndSubtopic(user, syllabus, topic, subtopic)
                .orElseGet(() -> SyllabusExplanation.builder()
                        .user(user)
                        .syllabus(syllabus)
                        .topic(topic)
                        .subtopic(subtopic)
                        .build());

        syllabusExplanation.setExplanation(explanation);
        return explanationRepository.save(syllabusExplanation);
    }

    @Transactional(readOnly = true)
    public List<SyllabusExplanation> getSavedExplanations(User user, Long syllabusId) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));
        return explanationRepository.findBySyllabusAndUserOrderByTopicAscSubtopicAsc(syllabus, user);
    }

    @Transactional
    public void deleteSyllabus(Long syllabusId, User user) {
        Syllabus syllabus = syllabusRepository.findByIdAndUser(syllabusId, user)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));
        explanationRepository.deleteBySyllabus(syllabus);
        syllabusRepository.delete(syllabus);
    }
}
