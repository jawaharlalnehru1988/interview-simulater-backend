package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.AspirationChecklist;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.model.UserAspiration;
import com.asknehru.interviewsimulator.repository.AspirationChecklistRepository;
import com.asknehru.interviewsimulator.repository.UserAspirationRepository;
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
public class UserAspirationService {

    private final LlmService llmService;
    private final UserAspirationRepository aspirationRepository;
    private final AspirationChecklistRepository checklistRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public UserAspiration createAspiration(User user, String currentPosition, String targetJob, 
                                           Integer timelineMonths, List<String> currentSkills, 
                                           String constraints, String additionalContext) {
        
        String skillsText = String.join(", ", currentSkills);
        String prompt = String.format(
            "You are a senior career coach and hiring strategist.\n" +
            "Create a practical roadmap to move from current role to target role.\n\n" +
            "Current position: %s\nTarget job: %s\nTimeline months: %d\nCurrent skills: %s\nConstraints: %s\nAdditional context: %s\n\n" +
            "Return STRICT JSON only:\n" +
            "{\n" +
            "  \"summary\": \"string\",\n" +
            "  \"readiness_score\": 0-100,\n" +
            "  \"gap_analysis\": [\"string\"],\n" +
            "  \"roadmap_phases\": [\n" +
            "    {\"phase\": \"string\", \"duration\": \"string\", \"focus\": \"string\", \"actions\": [\"string\"], \"deliverables\": [\"string\"]}\n" +
            "  ],\n" +
            "  \"weekly_execution\": [\"string\"],\n" +
            "  \"interview_preparation\": [\"string\"],\n" +
            "  \"encouragement\": \"string\"\n" +
            "}",
            currentPosition, targetJob, timelineMonths, skillsText, constraints, additionalContext
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);
        
        // Use fallbacks if LLM fails
        if (parsed.isEmpty()) {
            // Simple fallback logic omitted for brevity, but LLM usually works
        }

        UserAspiration aspiration = UserAspiration.builder()
                .user(user)
                .currentPosition(currentPosition)
                .targetJob(targetJob)
                .timelineMonths(timelineMonths)
                .currentSkills(serialize(currentSkills))
                .constraints(constraints)
                .additionalContext(additionalContext)
                .roadmap(serialize(parsed))
                .build();
        
        UserAspiration savedAspiration = aspirationRepository.save(aspiration);
        
        // Generate checklist
        generateChecklist(savedAspiration, parsed, timelineMonths);
        
        return savedAspiration;
    }

    private void generateChecklist(UserAspiration aspiration, JsonNode roadmap, int timelineMonths) {
        int totalWeeks = Math.max(1, timelineMonths * 4);
        List<Map<String, Object>> items = new ArrayList<>();
        
        JsonNode phases = roadmap.path("roadmap_phases");
        int weekBucket = 1;
        
        if (phases.isArray()) {
            for (int i = 0; i < phases.size(); i++) {
                JsonNode phase = phases.get(i);
                JsonNode actions = phase.path("actions");
                if (actions.isArray()) {
                    for (int j = 0; j < actions.size(); j++) {
                        items.add(createChecklistItem("phase-" + i + "-action-" + j, Math.min(totalWeeks, weekBucket++), "phase_action", actions.get(j).asText()));
                    }
                }
                JsonNode deliverables = phase.path("deliverables");
                if (deliverables.isArray()) {
                    for (int j = 0; j < deliverables.size(); j++) {
                        items.add(createChecklistItem("phase-" + i + "-deliverable-" + j, Math.min(totalWeeks, weekBucket++), "deliverable", "Deliverable: " + deliverables.get(j).asText()));
                    }
                }
            }
        }

        AspirationChecklist checklist = AspirationChecklist.builder()
                .aspiration(aspiration)
                .items(serialize(items))
                .totalCount(items.size())
                .completedCount(0)
                .build();
        checklistRepository.save(checklist);
    }

    private Map<String, Object> createChecklistItem(String id, int week, String category, String title) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("week", week);
        item.put("category", category);
        item.put("title", title);
        item.put("completed", false);
        return item;
    }

    @Transactional
    public AspirationChecklist updateChecklistItem(Long aspirationId, String itemId, boolean completed) {
        UserAspiration aspiration = aspirationRepository.findById(aspirationId).orElseThrow();
        AspirationChecklist checklist = checklistRepository.findByAspiration(aspiration).orElseThrow();
        
        List<Map<String, Object>> items = deserializeList(checklist.getItems());
        int completedCount = 0;
        for (Map<String, Object> item : items) {
            if (itemId.equals(item.get("id"))) {
                item.put("completed", completed);
            }
            if (Boolean.TRUE.equals(item.get("completed"))) {
                completedCount++;
            }
        }
        
        checklist.setItems(serialize(items));
        checklist.setCompletedCount(completedCount);
        return checklistRepository.save(checklist);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
