package com.asknehru.interviewsimulator.codingtrainer;
import com.asknehru.interviewsimulator.codingtrainer.dto.StartCodingTestRequest;
import com.asknehru.interviewsimulator.codingtrainer.dto.StartManipulationRequest;
import com.asknehru.interviewsimulator.codingtrainer.dto.EvaluateManipulationRequest;
import com.asknehru.interviewsimulator.codingtrainer.dto.SubmitCodingCodeRequest;
import com.asknehru.interviewsimulator.codingtrainer.dto.SubmitCodingApproachRequest;
import java.util.List;

import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.auth.UserRepository;
import com.asknehru.interviewsimulator.codingtrainer.CodingTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class CodingTestController {

    private final CodingTestService codingTestService;
    private final UserRepository userRepository;

    @PostMapping("/start")
    public ResponseEntity<?> generateCodingTest(@RequestBody StartCodingTestRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> test = codingTestService.generateCodingTest(user, request);
        return ResponseEntity.ok(test);
    }

    @PostMapping("/{interviewId}/question/{questionId}/approach")
    public ResponseEntity<?> submitApproach(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody SubmitCodingApproachRequest request) {
        Map<String, Object> result = codingTestService.submitApproach(interviewId, questionId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{interviewId}/question/{questionId}/direct")
    public ResponseEntity<?> submitDirect(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody Map<String, String> payload) {
        String answer = payload.get("answer");
        Map<String, Object> result = codingTestService.submitDirectAnswer(interviewId, questionId, answer);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{interviewId}/question/{questionId}/code")
    public ResponseEntity<?> submitCode(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody SubmitCodingCodeRequest request) {
        Map<String, Object> result = codingTestService.submitCode(interviewId, questionId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/manipulation/start")
    public ResponseEntity<?> generateManipulationQuestions(@RequestBody StartManipulationRequest request) {
        List<String> questions = codingTestService.generateManipulationQuestions(request);
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    @PostMapping("/manipulation/evaluate")
    public ResponseEntity<?> evaluateManipulationAnswers(@RequestBody EvaluateManipulationRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> result = codingTestService.evaluateManipulationAnswers(user, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/complexity/start")
    public ResponseEntity<?> generateComplexityQuestions(@RequestBody com.asknehru.interviewsimulator.codingtrainer.dto.StartComplexityRequest request) {
        List<String> questions = codingTestService.generateComplexityQuestions(request);
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    @PostMapping("/complexity/evaluate")
    public ResponseEntity<?> evaluateComplexityAnswers(@RequestBody com.asknehru.interviewsimulator.codingtrainer.dto.EvaluateComplexityRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> result = codingTestService.evaluateComplexityAnswers(user, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/manipulation/saved-sets")
    public ResponseEntity<?> saveQuestionSet(@RequestBody com.asknehru.interviewsimulator.codingtrainer.category.SavedQuestionSet request) {
        com.asknehru.interviewsimulator.codingtrainer.category.SavedQuestionSet saved = codingTestService.saveQuestionSet(request);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/manipulation/saved-sets")
    public ResponseEntity<?> getSavedSets(@RequestParam String topic, @RequestParam String category) {
        List<com.asknehru.interviewsimulator.codingtrainer.category.SavedQuestionSet> sets = codingTestService.getSavedQuestionSets(topic, category);
        return ResponseEntity.ok(sets);
    }

    @GetMapping("/manipulation/history")
    public ResponseEntity<?> getManipulationHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<Map<String, Object>> history = codingTestService.getManipulationHistory(user);
        return ResponseEntity.ok(history);
    }
}
