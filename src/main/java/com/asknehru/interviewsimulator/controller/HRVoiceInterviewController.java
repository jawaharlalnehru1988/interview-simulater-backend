package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.*;
import com.asknehru.interviewsimulator.repository.*;
import com.asknehru.interviewsimulator.service.HRVoiceInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/hr")
@RequiredArgsConstructor
public class HRVoiceInterviewController {

    private final HRVoiceInterviewService hrService;
    private final HRVoiceInterviewSessionRepository sessionRepository;
    private final CandidateProfileRepository profileRepository;
    private final UserAspirationRepository aspirationRepository;
    private final JobDescriptionAnalysisRepository jdRepository;
    private final UserRepository userRepository;

    @PostMapping("/start/")
    public ResponseEntity<?> start(@RequestBody Map<String, Long> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        CandidateProfile profile = profileRepository.findByUser(user).orElse(null);
        UserAspiration aspiration = request.containsKey("aspirationId") ? 
                aspirationRepository.findById(request.get("aspirationId")).orElse(null) : null;
        JobDescriptionAnalysis jobAnalysis = request.containsKey("jobAnalysisId") ? 
                jdRepository.findById(request.get("jobAnalysisId")).orElse(null) : null;

        HRVoiceInterviewSession session = hrService.startSession(user, profile, aspiration, jobAnalysis);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/answer/")
    public ResponseEntity<?> submitAnswer(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        String answer = request.get("answer");
        Map<String, Object> result = hrService.submitTurn(sessionId, answer);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<HRVoiceInterviewSession> history = sessionRepository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/")
    public ResponseEntity<?> getSession(@PathVariable Long id) {
        return sessionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
