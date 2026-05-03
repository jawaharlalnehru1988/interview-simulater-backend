package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.JobDescriptionAnalysis;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.JobDescriptionAnalyzerService;
import com.asknehru.interviewsimulator.repository.JobDescriptionAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/job-analyzer")
@RequiredArgsConstructor
public class JobDescriptionController {

    private final JobDescriptionAnalyzerService analyzerService;
    private final JobDescriptionAnalysisRepository repository;
    private final UserRepository userRepository;

    @PostMapping("/analyze/")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        
        String jobDescription = request.get("jobDescription");
        if (jobDescription == null || jobDescription.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "jobDescription is required"));
        }

        JobDescriptionAnalysis result = analyzerService.analyzeAndSave(user, jobDescription);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<JobDescriptionAnalysis> history = repository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/resume/")
    public ResponseEntity<?> getAnalysis(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
