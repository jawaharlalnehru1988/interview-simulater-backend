package com.asknehru.interviewsimulator.roadmap;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;
    private final RoadmapRepository roadmapRepository;

    @PostMapping("/api/interview/roadmap/import/{syllabusId}")
    public ResponseEntity<?> importRoadmap(@PathVariable Long syllabusId) {
        try {
            Roadmap roadmap = roadmapService.importFromSyllabus(syllabusId);
            return ResponseEntity.status(201).body(toRoadmapMap(roadmap));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    @GetMapping("/api/public/roadmaps/list")
    public ResponseEntity<?> listPublicRoadmaps() {
        List<Roadmap> roadmaps = roadmapRepository.findAllByOrderByCreatedAtDescIdDesc();
        List<Map<String, Object>> response = roadmaps.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("mainTopic", r.getMainTopic());
            map.put("routerLink", r.getRouterLink());
            map.put("createdAt", r.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/public/roadmaps/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getPublicRoadmapDetails(@PathVariable Long id) {
        return roadmapRepository.findById(id)
                .map(r -> ResponseEntity.ok(toRoadmapMap(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/public/roadmaps/explain")
    public ResponseEntity<?> explainRoadmapSubtopic(@RequestBody Map<String, Object> request) {
        Object subtopicIdObj = request.get("subtopicId");

        if (subtopicIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "subtopicId is required"));
        }

        try {
            Long subtopicId = Long.valueOf(subtopicIdObj.toString());
            String explanation = roadmapService.explainSubtopic(subtopicId);
            return ResponseEntity.ok(Map.of("explanation", explanation));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    private Map<String, Object> toRoadmapMap(Roadmap roadmap) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", roadmap.getId());
        map.put("mainTopic", roadmap.getMainTopic());
        map.put("routerLink", roadmap.getRouterLink());
        map.put("createdAt", roadmap.getCreatedAt() != null ? roadmap.getCreatedAt().toString() : null);
        map.put("updatedAt", roadmap.getUpdatedAt() != null ? roadmap.getUpdatedAt().toString() : null);

        List<Map<String, Object>> chapters = roadmap.getChapters().stream().map(c -> {
            Map<String, Object> cmap = new HashMap<>();
            cmap.put("id", c.getId());
            cmap.put("chapterName", c.getChapterName());
            List<Map<String, Object>> subtopics = c.getSubtopics().stream().map(s -> {
                Map<String, Object> smap = new HashMap<>();
                smap.put("id", s.getId());
                smap.put("subtopicName", s.getSubtopicName());
                smap.put("explanation", s.getExplanation());
                return smap;
            }).collect(Collectors.toList());
            cmap.put("subtopics", subtopics);
            return cmap;
        }).collect(Collectors.toList());

        map.put("chapters", chapters);
        return map;
    }
}
