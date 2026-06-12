package com.asknehru.interviewsimulator.candidate;

import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.auth.UserRepository;
import com.asknehru.interviewsimulator.candidate.UserProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class UserProgressController {

    private final UserProgressService progressService;
    private final UserRepository userRepository;

    @GetMapping("/progress/")
    public ResponseEntity<?> getProgress() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        Map<String, Object> progress = progressService.getUserProgress(user);
        return ResponseEntity.ok(progress);
    }
}
