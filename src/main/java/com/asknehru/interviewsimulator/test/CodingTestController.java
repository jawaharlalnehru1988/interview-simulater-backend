package com.asknehru.interviewsimulator.test;
import com.asknehru.interviewsimulator.test.dto.StartCodingTestRequest;
import com.asknehru.interviewsimulator.test.dto.SubmitCodingCodeRequest;
import com.asknehru.interviewsimulator.test.dto.SubmitCodingApproachRequest;

import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.auth.UserRepository;
import com.asknehru.interviewsimulator.test.CodingTestService;
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

    @PostMapping("/generate")
    public ResponseEntity<?> generateCodingTest(@RequestBody StartCodingTestRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> test = codingTestService.generateCodingTest(user, request);
        return ResponseEntity.ok(test);
    }

    @PostMapping("/{interviewId}/{questionId}/submit-approach")
    public ResponseEntity<?> submitApproach(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody SubmitCodingApproachRequest request) {
        Map<String, Object> result = codingTestService.submitApproach(interviewId, questionId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{interviewId}/{questionId}/submit-direct")
    public ResponseEntity<?> submitDirect(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody Map<String, String> payload) {
        String answer = payload.get("answer");
        Map<String, Object> result = codingTestService.submitDirectAnswer(interviewId, questionId, answer);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{interviewId}/{questionId}/submit-code")
    public ResponseEntity<?> submitCode(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody SubmitCodingCodeRequest request) {
        Map<String, Object> result = codingTestService.submitCode(interviewId, questionId, request);
        return ResponseEntity.ok(result);
    }
}
