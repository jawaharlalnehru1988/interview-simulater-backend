package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.PersonalCoachSession;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.PersonalCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/interview/coach")
@RequiredArgsConstructor
public class PersonalCoachController {

    private final PersonalCoachService coachService;
    private final UserRepository userRepository;

    @PostMapping("/start/")
    public ResponseEntity<?> startCoach(@RequestBody Map<String, String> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        
        String topic = request.get("topic");
        if (topic == null || topic.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "topic is required"));
        }

        Map<String, Object> sessionData = coachService.startOrResumeSession(user, topic);
        return ResponseEntity.ok(sessionData);
    }

    @GetMapping("/{sessionId}/resume/")
    public ResponseEntity<?> resumeCoach(@PathVariable Long sessionId) {
        Map<String, Object> sessionData = coachService.getSessionData(sessionId);
        return ResponseEntity.ok(sessionData);
    }

    @PostMapping("/{sessionId}/choose-subtopic/")
    public ResponseEntity<?> chooseSubtopic(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        String subtopic = request.get("subtopic");
        Map<String, Object> result = coachService.chooseSubtopic(sessionId, subtopic);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionId}/choose-lesson/")
    public ResponseEntity<?> chooseLesson(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        String lesson = request.get("lesson");
        Map<String, Object> result = coachService.chooseLesson(sessionId, lesson);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionId}/answer/")
    public ResponseEntity<?> submitAnswer(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        String answer = request.get("answer");
        Map<String, Object> evaluation = coachService.evaluateAnswer(sessionId, answer);
        return ResponseEntity.ok(evaluation);
    }
}
