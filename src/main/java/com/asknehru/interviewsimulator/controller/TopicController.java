package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.Topic;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.TopicRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    private static final List<String> PREDEFINED_TOPICS = List.of(
        "Java 8", "DSA", "Javascript", "Angular", "Springboot",
        "Microservices(Java)", "React", "NextJS", "Devops",
        "API design", "Agentic AI", "Frontend SystemDesign",
        "backend SystemDesign", "backend security"
    );

    @GetMapping("/")
    public ResponseEntity<?> getTopics() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Topic> topics = topicRepository.findByUserOrderByCreatedAtDesc(user);
        if (topics.isEmpty()) {
            topics = new ArrayList<>();
            for (String name : PREDEFINED_TOPICS) {
                Topic defaultTopic = Topic.builder()
                        .user(user)
                        .name(name)
                        .description("Predefined learning track for " + name)
                        .build();
                topics.add(topicRepository.save(defaultTopic));
            }
            // Sort by ID to keep the predefined order, or just return them
        }

        return ResponseEntity.ok(topics.stream().map(t -> Map.of(
                "id", t.getId(),
                "name", t.getName(),
                "description", t.getDescription() != null ? t.getDescription() : "",
                "created_at", t.getCreatedAt().toString()
        )).toList());
    }

    @PostMapping("/")
    public ResponseEntity<?> createTopic(@RequestBody Map<String, String> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        String name = request.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Topic name is required"));
        }

        String description = request.get("description");

        Topic topic = Topic.builder()
                .user(user)
                .name(name.trim())
                .description(description != null ? description.trim() : "")
                .build();

        Topic saved = topicRepository.save(topic);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "description", saved.getDescription() != null ? saved.getDescription() : "",
                "created_at", saved.getCreatedAt().toString()
        ));
    }
}
