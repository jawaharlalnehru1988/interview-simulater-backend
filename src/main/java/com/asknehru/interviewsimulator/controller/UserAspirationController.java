package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.AspirationChecklist;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.model.UserAspiration;
import com.asknehru.interviewsimulator.repository.AspirationChecklistRepository;
import com.asknehru.interviewsimulator.repository.UserAspirationRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.UserAspirationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/interview/aspiration")
@RequiredArgsConstructor
public class UserAspirationController {

    private final UserAspirationService aspirationService;
    private final UserAspirationRepository aspirationRepository;
    private final AspirationChecklistRepository checklistRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> toChecklistMap(AspirationChecklist checklist) {
        List<Map<String, Object>> itemsList = List.of();
        try {
            if (checklist.getItems() != null && !checklist.getItems().isEmpty()) {
                itemsList = objectMapper.readValue(checklist.getItems(), new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            // ignore
        }

        // Group by week
        Map<Integer, List<Map<String, Object>>> groupedByWeek = new TreeMap<>();
        for (Map<String, Object> item : itemsList) {
            int week = (Integer) item.get("week");
            groupedByWeek.computeIfAbsent(week, k -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> weeksList = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : groupedByWeek.entrySet()) {
            Map<String, Object> weekMap = new HashMap<>();
            weekMap.put("week", entry.getKey());
            weekMap.put("items", entry.getValue());
            weeksList.add(weekMap);
        }

        double progressPercent = 0.0;
        if (checklist.getTotalCount() != null && checklist.getTotalCount() > 0) {
            progressPercent = Math.round(((double) checklist.getCompletedCount() / checklist.getTotalCount() * 100.0) * 100.0) / 100.0;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", checklist.getId());
        response.put("completed_count", checklist.getCompletedCount() != null ? checklist.getCompletedCount() : 0);
        response.put("total_count", checklist.getTotalCount() != null ? checklist.getTotalCount() : 0);
        response.put("progress_percent", progressPercent);
        response.put("items", itemsList);
        response.put("weeks", weeksList);
        return response;
    }

    private Map<String, Object> toAspirationMap(UserAspiration aspiration) {
        List<String> currentSkills = List.of();
        Map<String, Object> roadmap = Map.of();
        try {
            if (aspiration.getCurrentSkills() != null && !aspiration.getCurrentSkills().isEmpty()) {
                currentSkills = objectMapper.readValue(aspiration.getCurrentSkills(), new TypeReference<List<String>>() {});
            }
            if (aspiration.getRoadmap() != null && !aspiration.getRoadmap().isEmpty()) {
                roadmap = objectMapper.readValue(aspiration.getRoadmap(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> map = new HashMap<>();
        map.put("aspiration_id", aspiration.getId());
        map.put("current_position", aspiration.getCurrentPosition());
        map.put("target_job", aspiration.getTargetJob());
        map.put("timeline_months", aspiration.getTimelineMonths());
        map.put("current_skills", currentSkills);
        map.put("constraints", aspiration.getConstraints() != null ? aspiration.getConstraints() : "");
        map.put("additional_context", aspiration.getAdditionalContext() != null ? aspiration.getAdditionalContext() : "");
        map.put("roadmap", roadmap);
        map.put("created_at", aspiration.getCreatedAt().toString());
        map.put("last_updated", aspiration.getUpdatedAt().toString());

        checklistRepository.findByAspiration(aspiration).ifPresent(c -> {
            map.put("checklist", toChecklistMap(c));
        });

        return map;
    }

    @PostMapping("/create/")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        // Safe integer parsing
        Integer timeline = 6;
        if (request.containsKey("timeline_months")) {
            Object obj = request.get("timeline_months");
            if (obj instanceof Number) {
                timeline = ((Number) obj).intValue();
            } else if (obj != null) {
                timeline = Integer.parseInt(obj.toString());
            }
        }

        UserAspiration result = aspirationService.createAspiration(
                user,
                (String) request.get("current_position"),
                (String) request.get("target_job"),
                timeline,
                (List<String>) request.get("current_skills"),
                (String) request.get("constraints"),
                (String) request.get("additional_context")
        );
        return ResponseEntity.status(201).body(toAspirationMap(result));
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<UserAspiration> history = aspirationRepository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history.stream().map(this::toAspirationMap).toList());
    }

    @GetMapping("/{id}/resume/")
    public ResponseEntity<?> getAspiration(@PathVariable Long id) {
        return aspirationRepository.findById(id)
                .map(a -> ResponseEntity.ok(toAspirationMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/checklist/")
    public ResponseEntity<?> getChecklist(@PathVariable Long id) {
        UserAspiration aspiration = aspirationRepository.findById(id).orElseThrow();
        return checklistRepository.findByAspiration(aspiration)
                .map(c -> ResponseEntity.ok(toChecklistMap(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/checklist/generate/")
    public ResponseEntity<?> generateChecklist(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        boolean forceRegenerate = request.containsKey("force_regenerate") && (Boolean) request.get("force_regenerate");
        UserAspiration aspiration = aspirationRepository.findById(id).orElseThrow();
        AspirationChecklist checklist = aspirationService.generateChecklistForAspiration(aspiration, forceRegenerate);
        return ResponseEntity.ok(toChecklistMap(checklist));
    }

    @PostMapping("/{id}/checklist/toggle/")
    public ResponseEntity<?> toggleChecklist(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String itemId = request.containsKey("item_id") ? (String) request.get("item_id") : (String) request.get("itemId");
        boolean completed = (Boolean) request.get("completed");
        AspirationChecklist result = aspirationService.updateChecklistItem(id, itemId, completed);
        return ResponseEntity.ok(toChecklistMap(result));
    }

    @PostMapping("/{id}/checklist/update/")
    public ResponseEntity<?> updateChecklist(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return toggleChecklist(id, request);
    }
}
