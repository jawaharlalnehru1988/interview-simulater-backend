package com.asknehru.interviewsimulator.repositoryai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repository")
@RequiredArgsConstructor
public class RepositoryAiController {

    private final RepositoryAnalysisRepository analysisRepository;
    private final RepositoryChatRepository chatRepository;
    private final RepositoryAiService repositoryAiService;

    @PostMapping("/analyze")
    public ResponseEntity<RepositoryAnalysis> triggerAnalysis(@RequestBody RepositoryRequest request) {
        RepositoryAnalysis analysis = new RepositoryAnalysis();
        analysis.setGithubUrl(request.getGithubUrl());
        analysis.setStatus("PENDING");
        analysis = analysisRepository.save(analysis);

        repositoryAiService.analyzeRepository(analysis.getId(), request.getGithubUrl());

        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/analyze")
    public ResponseEntity<List<RepositoryAnalysis>> getAllAnalyses() {
        return ResponseEntity.ok(analysisRepository.findAll());
    }

    @GetMapping("/analyze/{id}")
    public ResponseEntity<RepositoryAnalysis> getAnalysis(@PathVariable Long id) {
        return analysisRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<Map<String, String>> chat(@PathVariable Long id, @RequestBody ChatRequest request) {
        String response = repositoryAiService.handleChat(id, request.getMessage());
        return ResponseEntity.ok(Map.of("response", response));
    }

    @GetMapping("/{id}/chat")
    public ResponseEntity<List<RepositoryChat>> getChatHistory(@PathVariable Long id) {
        return ResponseEntity.ok(chatRepository.findByRepositoryAnalysisIdOrderByCreatedAtAsc(id));
    }

    @DeleteMapping("/analyze/{id}")
    public ResponseEntity<Void> deleteAnalysis(@PathVariable Long id) {
        if (!analysisRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        chatRepository.deleteByRepositoryAnalysisId(id);
        analysisRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
