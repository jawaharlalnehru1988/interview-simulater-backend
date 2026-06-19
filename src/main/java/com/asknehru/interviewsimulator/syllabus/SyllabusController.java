package com.asknehru.interviewsimulator.syllabus;

import com.asknehru.interviewsimulator.syllabus.Syllabus;
import com.asknehru.interviewsimulator.syllabus.SyllabusExplanation;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.syllabus.SyllabusRepository;
import com.asknehru.interviewsimulator.auth.UserRepository;
import com.asknehru.interviewsimulator.syllabus.SyllabusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/interview/syllabus")
@RequiredArgsConstructor
public class SyllabusController {

    private final SyllabusService syllabusService;
    private final SyllabusRepository syllabusRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> toSyllabusMap(Syllabus s) {
        Object syllabusList = List.of();
        Object checklistList = List.of();
        try {
            if (s.getSyllabusContent() != null && !s.getSyllabusContent().isEmpty()) {
                syllabusList = objectMapper.readValue(s.getSyllabusContent(), new TypeReference<List<Map<String, Object>>>() {});
            }
            if (s.getChecklistContent() != null && !s.getChecklistContent().isEmpty()) {
                checklistList = objectMapper.readValue(s.getChecklistContent(), new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("topic", s.getTopic());
        map.put("syllabus", syllabusList);
        map.put("converted_to_checklist", s.getConvertedToChecklist());
        map.put("checklist", checklistList);
        map.put("created_at", s.getCreatedAt().toString());
        map.put("updated_at", s.getUpdatedAt().toString());
        return map;
    }

    @PostMapping("/generate/")
    public ResponseEntity<?> generate(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        String topic = (String) request.get("topic");
        String description = (String) request.get("description");
        String sourceText = (String) request.get("sourceText");
        
        if (topic == null || topic.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Topic is required"));
        }

        Syllabus result = syllabusService.generateSyllabus(user, topic.trim(), description, sourceText);
        return ResponseEntity.status(201).body(toSyllabusMap(result));
    }

    @PostMapping("/{id}/convert/")
    public ResponseEntity<?> convert(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        Syllabus result = syllabusService.convertToChecklist(id, user);
        return ResponseEntity.ok(toSyllabusMap(result));
    }

    @PostMapping("/{id}/toggle/")
    public ResponseEntity<?> toggle(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        String itemId = (String) request.get("item_id");
        if (itemId == null) {
            itemId = (String) request.get("itemId");
        }
        Boolean completed = (Boolean) request.get("completed");

        if (itemId == null || completed == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "item_id and completed parameters are required"));
        }

        Syllabus result = syllabusService.toggleChecklistItem(id, user, itemId, completed);
        return ResponseEntity.ok(toSyllabusMap(result));
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Syllabus> history = syllabusRepository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history.stream().map(this::toSyllabusMap).toList());
    }

    @GetMapping("/{id}/")
    public ResponseEntity<?> getDetails(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        return syllabusRepository.findByIdAndUser(id, user)
                .map(s -> ResponseEntity.ok(toSyllabusMap(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public/list")
    public ResponseEntity<?> getPublicList() {
        List<Syllabus> list = syllabusRepository.findAll();
        List<Map<String, Object>> summaryList = list.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("topic", s.getTopic());
            return map;
        }).toList();
        return ResponseEntity.ok(summaryList);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicDetails(@PathVariable Long id) {
        return syllabusRepository.findById(id)
                .map(s -> ResponseEntity.ok(toSyllabusMap(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toExplanationMap(SyllabusExplanation exp) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", exp.getId());
        map.put("topic", exp.getTopic());
        map.put("subtopic", exp.getSubtopic());
        map.put("explanation", exp.getExplanation());
        map.put("created_at", exp.getCreatedAt().toString());
        map.put("updated_at", exp.getUpdatedAt().toString());
        return map;
    }

    @PostMapping("/explain")
    public ResponseEntity<?> explain(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        Long syllabusId = null;
        if (request.containsKey("syllabus_id")) {
            syllabusId = ((Number) request.get("syllabus_id")).longValue();
        } else if (request.containsKey("syllabusId")) {
            syllabusId = ((Number) request.get("syllabusId")).longValue();
        }
        String topic = (String) request.get("topic");
        String subtopic = (String) request.get("subtopic");

        if (syllabusId == null || topic == null || subtopic == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "syllabus_id, topic, and subtopic parameters are required"));
        }

        String explanation = syllabusService.generateExplanation(user, syllabusId, topic, subtopic);
        return ResponseEntity.ok(Map.of("explanation", explanation));
    }

    @PostMapping("/explanation/save")
    public ResponseEntity<?> saveExplanation(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        Long syllabusId = null;
        if (request.containsKey("syllabus_id")) {
            syllabusId = ((Number) request.get("syllabus_id")).longValue();
        } else if (request.containsKey("syllabusId")) {
            syllabusId = ((Number) request.get("syllabusId")).longValue();
        }
        String topic = (String) request.get("topic");
        String subtopic = (String) request.get("subtopic");
        String explanation = (String) request.get("explanation");

        if (syllabusId == null || topic == null || subtopic == null || explanation == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "syllabus_id, topic, subtopic, and explanation parameters are required"));
        }

        SyllabusExplanation result = syllabusService.saveExplanation(user, syllabusId, topic, subtopic, explanation);
        return ResponseEntity.ok(toExplanationMap(result));
    }

    @GetMapping("/{id}/explanations")
    public ResponseEntity<?> getExplanations(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<SyllabusExplanation> result = syllabusService.getSavedExplanations(user, id);
        return ResponseEntity.ok(result.stream().map(this::toExplanationMap).toList());
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<?> deleteSyllabus(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        try {
            syllabusService.deleteSyllabus(id, user);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }
}
