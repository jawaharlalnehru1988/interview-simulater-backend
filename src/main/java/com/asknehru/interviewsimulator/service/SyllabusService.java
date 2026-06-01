package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.Syllabus;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.SyllabusRepository;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Syllabus generateSyllabus(User user, String topic) {
        String prompt = String.format(
            "You are an expert technical instructor and curriculum designer.\n" +
            "Create a highly structured and comprehensive learning syllabus for the topic: %s.\n\n" +
            "Return STRICT JSON only, containing a list of objects representing the chapters/topics, each containing a list of subtopics. " +
            "Do NOT wrap it in any JSON key, just return a raw JSON list:\n" +
            "[\n" +
            "  {\n" +
            "    \"title\": \"Chapter 1: Title\",\n" +
            "    \"subtopics\": [\"Subtopic A\", \"Subtopic B\"]\n" +
            "  }\n" +
            "]",
            topic
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        // Fallback if LLM fails
        if (parsed.isEmpty() || !parsed.isArray()) {
            List<Map<String, Object>> fallbackList = new ArrayList<>();
            Map<String, Object> c1 = new HashMap<>();
            c1.put("title", "Chapter 1: Introduction to " + topic);
            c1.put("subtopics", List.of("Core Concepts", "Basic Setup and Installation", "Hello World Example"));
            Map<String, Object> c2 = new HashMap<>();
            c2.put("title", "Chapter 2: Intermediate " + topic);
            c2.put("subtopics", List.of("Configuration Options", "Best Practices", "Common Use Cases"));
            fallbackList.add(c1);
            fallbackList.add(c2);
            try {
                parsed = objectMapper.valueToTree(fallbackList);
            } catch (Exception e) {
                // ignore
            }
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
}
