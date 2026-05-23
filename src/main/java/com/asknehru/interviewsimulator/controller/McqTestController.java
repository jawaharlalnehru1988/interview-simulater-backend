package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.dto.StartMcqTestRequest;
import com.asknehru.interviewsimulator.dto.SubmitMcqTestRequest;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.McqTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mcq-test")
@RequiredArgsConstructor
public class McqTestController {

    private final McqTestService mcqTestService;
    private final UserRepository userRepository;

    @PostMapping("/generate")
    public ResponseEntity<?> generateMcqTest(@RequestBody StartMcqTestRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> test = mcqTestService.generateMcqTest(user, request);
        return ResponseEntity.ok(test);
    }

    @PostMapping("/{interviewId}/submit")
    public ResponseEntity<?> submitMcqTest(@PathVariable Long interviewId, @RequestBody SubmitMcqTestRequest request) {
        Map<String, Object> result = mcqTestService.submitMcqTest(interviewId, request);
        return ResponseEntity.ok(result);
    }
}
