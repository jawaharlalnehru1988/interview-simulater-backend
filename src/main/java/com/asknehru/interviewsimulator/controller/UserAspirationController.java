package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.AspirationChecklist;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.model.UserAspiration;
import com.asknehru.interviewsimulator.repository.AspirationChecklistRepository;
import com.asknehru.interviewsimulator.repository.UserAspirationRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.asknehru.interviewsimulator.service.UserAspirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/aspiration")
@RequiredArgsConstructor
public class UserAspirationController {

    private final UserAspirationService aspirationService;
    private final UserAspirationRepository aspirationRepository;
    private final AspirationChecklistRepository checklistRepository;
    private final UserRepository userRepository;

    @PostMapping("/create/")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        UserAspiration result = aspirationService.createAspiration(
                user,
                (String) request.get("current_position"),
                (String) request.get("target_job"),
                (Integer) request.get("timeline_months"),
                (List<String>) request.get("current_skills"),
                (String) request.get("constraints"),
                (String) request.get("additional_context")
        );
        return ResponseEntity.status(201).body(Map.of("aspiration_id", result.getId()));
    }

    @GetMapping("/history/")
    public ResponseEntity<?> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<UserAspiration> history = aspirationRepository.findByUserOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/resume/")
    public ResponseEntity<?> getAspiration(@PathVariable Long id) {
        return aspirationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/checklist/")
    public ResponseEntity<?> getChecklist(@PathVariable Long id) {
        UserAspiration aspiration = aspirationRepository.findById(id).orElseThrow();
        return checklistRepository.findByAspiration(aspiration)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/checklist/update/")
    public ResponseEntity<?> updateChecklist(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String itemId = (String) request.get("itemId");
        boolean completed = (Boolean) request.get("completed");
        AspirationChecklist result = aspirationService.updateChecklistItem(id, itemId, completed);
        return ResponseEntity.ok(result);
    }
}
