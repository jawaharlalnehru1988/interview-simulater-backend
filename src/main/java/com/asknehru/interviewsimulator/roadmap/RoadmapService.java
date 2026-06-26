package com.asknehru.interviewsimulator.roadmap;

import com.asknehru.interviewsimulator.ai.LlmService;
import com.asknehru.interviewsimulator.syllabus.Syllabus;
import com.asknehru.interviewsimulator.syllabus.SyllabusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final SyllabusRepository syllabusRepository;
    private final RoadmapSubtopicRepository roadmapSubtopicRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Roadmap importFromSyllabus(Long syllabusId) {
        Syllabus syllabus = syllabusRepository.findById(syllabusId)
                .orElseThrow(() -> new RuntimeException("Syllabus not found"));

        try {
            String topic = syllabus.getTopic();
            String routerLink = topic.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

            java.util.Optional<Roadmap> existingRoadmap = roadmapRepository.findByRouterLink(routerLink);
            if (existingRoadmap.isPresent()) {
                return existingRoadmap.get();
            }

            List<Map<String, Object>> chaptersList = objectMapper.readValue(
                    syllabus.getSyllabusContent(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            Roadmap roadmap = new Roadmap();
            roadmap.setMainTopic(topic.trim());
            roadmap.setRouterLink(routerLink);
            
            if (chaptersList != null) {
                for (Map<String, Object> chMap : chaptersList) {
                    String title = (String) chMap.get("title");
                    List<String> subtopicsList = (List<String>) chMap.get("subtopics");
                    
                    RoadmapChapter chapter = new RoadmapChapter();
                    chapter.setChapterName(title.trim());
                    chapter.setRoadmap(roadmap);
                    
                    if (subtopicsList != null) {
                        for (String subtopicName : subtopicsList) {
                            RoadmapSubtopic subtopic = new RoadmapSubtopic();
                            subtopic.setSubtopicName(subtopicName.trim());
                            subtopic.setChapter(chapter);
                            chapter.getSubtopics().add(subtopic);
                        }
                    }
                    roadmap.getChapters().add(chapter);
                }
            }

            return roadmapRepository.save(roadmap);

        } catch (Exception e) {
            log.error("Failed to import syllabus to roadmap: {}", e.getMessage());
            throw new RuntimeException("Failed to import syllabus to roadmap");
        }
    }

    @Transactional
    public String explainSubtopic(Long subtopicId) {
        RoadmapSubtopic subtopic = roadmapSubtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new RuntimeException("Subtopic not found"));

        if (subtopic.getExplanation() != null && !subtopic.getExplanation().trim().isEmpty()) {
            return subtopic.getExplanation();
        }

        String mainTopic = subtopic.getChapter().getRoadmap().getMainTopic();
        String subtopicName = subtopic.getSubtopicName();

        String prompt = String.format(
            "You are an expert instructor.\n" +
            "Topic: %s\n" +
            "Subtopic: %s\n\n" +
            "Please provide a comprehensive, detailed explanation of this subtopic in the context of the main topic. " +
            "Use Markdown formatting and explain it clearly. " +
            "CRITICAL INSTRUCTION: ONLY provide code examples or pseudo-code if the topic is inherently technical or programming-related. " +
            "Do NOT provide code examples for non-technical topics (e.g., yoga, history, soft skills, general wellness). " +
            "If providing code examples for technical topics, prefer using Java and Spring Boot unless the topic specifically requires another language.",
            mainTopic, subtopicName
        );
        String explanation = llmService.generate(prompt);
        subtopic.setExplanation(explanation);
        roadmapSubtopicRepository.save(subtopic);
        return explanation;
    }

    @Transactional(readOnly = true)
    public void exportRoadmap(Long id, String domain) {
        Roadmap roadmap = roadmapRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Roadmap not found"));

        String targetUrl;
        if ("asknehru".equalsIgnoreCase(domain)) {
            targetUrl = "http://localhost:8083/api/roadmaps/import-syllabus/push-roadmap";
        } else if ("askharekrishna".equalsIgnoreCase(domain)) {
            targetUrl = "http://localhost:8001/api/course-roadmap/import-syllabus/push-roadmap";
        } else {
            throw new RuntimeException("Domain " + domain + " is currently not supported for export.");
        }

        try {
            List<Map<String, Object>> chaptersList = roadmap.getChapters().stream().map(c -> {
                Map<String, Object> cmap = new java.util.HashMap<>();
                cmap.put("chapterName", c.getChapterName());
                List<Map<String, String>> subtopics = c.getSubtopics().stream().map(s -> {
                    Map<String, String> smap = new java.util.HashMap<>();
                    smap.put("subtopicName", s.getSubtopicName());
                    return smap;
                }).collect(java.util.stream.Collectors.toList());
                cmap.put("subtopics", subtopics);
                return cmap;
            }).collect(java.util.stream.Collectors.toList());

            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("mainTopic", roadmap.getMainTopic());
            payload.put("routerLink", roadmap.getRouterLink());
            payload.put("intro", "Curated learning path exported from Interview Trainer.");
            payload.put("chapters", chaptersList);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");

            if ("askharekrishna".equalsIgnoreCase(domain)) {
                String auth = "narasimha:Bala#$88";
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            java.net.http.HttpRequest request = requestBuilder
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new RuntimeException("Target responded with status " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            log.error("Failed to export roadmap: {}", e.getMessage());
            throw new RuntimeException("Export failed: " + e.getMessage());
        }
    }
}
