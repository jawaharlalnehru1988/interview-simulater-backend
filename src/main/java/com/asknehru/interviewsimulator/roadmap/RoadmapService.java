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
            "You are an expert technical instructor.\n" +
            "Topic: %s\n" +
            "Subtopic: %s\n\n" +
            "Please provide a comprehensive, detailed explanation of this subtopic in the context of the main topic. " +
            "Use Markdown formatting, provide code examples if applicable, and explain it clearly.",
            mainTopic, subtopicName
        );
        String explanation = llmService.generate(prompt);
        subtopic.setExplanation(explanation);
        roadmapSubtopicRepository.save(subtopic);
        return explanation;
    }
}
