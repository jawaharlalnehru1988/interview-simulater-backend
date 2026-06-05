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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/job-analyzer")
@RequiredArgsConstructor
public class JobDescriptionController {

    private final JobDescriptionAnalyzerService analyzerService;
    private final JobDescriptionAnalysisRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> toAnalysisMap(JobDescriptionAnalysis a) {
        Map<String, Object> map = new HashMap<>();
        map.put("analysis_id", a.getId());
        map.put("job_description", a.getJobDescription());
        
        Object analysisObj = new HashMap<>();
        try {
            if (a.getAnalysis() != null && !a.getAnalysis().isEmpty()) {
                analysisObj = objectMapper.readValue(a.getAnalysis(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {}
        map.put("analysis", analysisObj);
        
        Map<String, Object> context = new HashMap<>();
        context.put("recruiter_name", a.getRecruiterName());
        context.put("company_name", a.getCompanyName());
        context.put("application_last_date", a.getApplicationLastDate() != null ? a.getApplicationLastDate().toString() : null);
        context.put("application_last_date_raw", a.getApplicationLastDateRaw());
        map.put("application_context", context);
        
        map.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        map.put("last_updated", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        
        return map;
    }

    @PostMapping("/analyze/")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        
        String jobDescription = request.containsKey("jobDescription") ? request.get("jobDescription") : request.get("job_description");
        if (jobDescription == null || jobDescription.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "job_description is required"));
        }

        JobDescriptionAnalysis result = analyzerService.analyzeAndSave(user, jobDescription);
        return ResponseEntity.ok(toAnalysisMap(result));
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<JobDescriptionAnalysis> history = repository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history.stream().map(this::toAnalysisMap).toList());
    }

    @GetMapping("/{id}/resume/")
    public ResponseEntity<?> getAnalysis(@PathVariable Long id) {
        return repository.findById(id)
                .map(a -> ResponseEntity.ok(toAnalysisMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }
}
