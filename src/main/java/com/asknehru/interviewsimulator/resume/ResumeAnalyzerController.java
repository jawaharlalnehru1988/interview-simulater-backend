package com.asknehru.interviewsimulator.resume;

import com.asknehru.interviewsimulator.auth.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Slf4j
public class ResumeAnalyzerController {

    private final ResumeExtractionService extractionService;
    private final ResumeAiService aiService;
    private final ResumeStorageService storageService;

    @PostMapping("/analyze/")
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are supported"));
        }

        String resumeText;
        try {
            resumeText = extractionService.extractTextFromPdf(file);
        } catch (IOException e) {
            log.error("Failed to extract PDF text: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read PDF: " + e.getMessage()));
        }

        if (resumeText == null || resumeText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No readable text found in the PDF. It may be a scanned image."));
        }

        JsonNode questionsNode;
        try {
            questionsNode = aiService.generateQuestions(resumeText);
        } catch (Exception e) {
            log.error("Failed to generate questions: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "LLM did not return a response. Check API key configuration."));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extractedTextLength", resumeText.length());
        result.put("questions", questionsNode);

        return ResponseEntity.ok(result);
    }

    public record QuestionDto(String question, String category, String difficulty, String hint, List<String> answers) {}
    public record SaveAnalysisRequest(String resumeFilename, String extractedText, List<QuestionDto> questions) {}

    @PostMapping("/save/")
    public ResponseEntity<?> saveAnalysis(@RequestBody SaveAnalysisRequest request, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        ResumeAnalyser analysis = storageService.saveAnalysis(user, request.resumeFilename(), request.extractedText(), request.questions());
        return ResponseEntity.ok(Map.of("message", "Saved successfully", "id", analysis.getId()));
    }

    @PutMapping("/save/{id}")
    public ResponseEntity<?> updateAnalysis(@PathVariable Long id, @RequestBody SaveAnalysisRequest request, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Optional<ResumeAnalyser> opt = storageService.updateAnalysis(id, user, request.questions());
        if (opt.isEmpty()) {
             return ResponseEntity.status(404).body(Map.of("error", "Analysis not found or unauthorized"));
        }

        return ResponseEntity.ok(Map.of("message", "Updated successfully", "id", opt.get().getId()));
    }

    @GetMapping("/saved/")
    public ResponseEntity<?> getSavedAnalyses(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        List<ResumeAnalyser> list = storageService.getAnalysesByUser(user);

        List<Map<String, Object>> result = list.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("resumeFilename", a.getResumeFilename());
            map.put("extractedText", a.getExtractedText());
            map.put("questions", a.getQuestions().stream().map(q -> {
                Map<String, Object> qMap = new LinkedHashMap<>();
                qMap.put("question", q.getQuestion());
                qMap.put("category", q.getCategory());
                qMap.put("difficulty", q.getDifficulty());
                qMap.put("hint", q.getHint());
                qMap.put("answers", q.getGeneratedAnswers() != null ? q.getGeneratedAnswers().toList() : new ArrayList<>());
                return qMap;
            }).toList());
            map.put("createdAt", a.getCreatedAt());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    public record AnswerRequest(Long savedAnalysisId, String question, String resumeContext) {}

    @PostMapping("/answers/")
    public ResponseEntity<?> generateAnswers(@RequestBody AnswerRequest request, @AuthenticationPrincipal User user) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }

        String resumeContext = request.resumeContext() != null ? request.resumeContext() : "";
        
        JsonNode answersNode;
        try {
            answersNode = aiService.generateAnswers(request.question(), resumeContext);
        } catch (Exception e) {
            log.error("Failed to generate answers: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "LLM did not return a response."));
        }

        if (request.savedAnalysisId() != null && user != null) {
            try {
                storageService.saveGeneratedAnswers(request.savedAnalysisId(), user, request.question(), answersNode);
            } catch (Exception e) {
                log.error("Failed to update saved analysis with generated answers", e);
            }
        }

        return ResponseEntity.ok(Map.of("answers", answersNode));
    }
}
