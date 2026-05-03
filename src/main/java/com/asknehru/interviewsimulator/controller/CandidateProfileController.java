package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.CandidateProfile;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.CandidateProfileRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/candidate")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/profile/")
    public ResponseEntity<?> getProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        CandidateProfile profile = profileRepository.findByUser(user)
                .orElseGet(() -> profileRepository.save(CandidateProfile.builder().user(user).build()));
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile/")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> request) throws JsonProcessingException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        
        CandidateProfile profile = profileRepository.findByUser(user)
                .orElseGet(() -> CandidateProfile.builder().user(user).build());

        if (request.containsKey("current_position")) profile.setCurrentPosition((String) request.get("current_position"));
        if (request.containsKey("current_company")) profile.setCurrentCompany((String) request.get("current_company"));
        if (request.containsKey("total_experience_years")) {
            Object exp = request.get("total_experience_years");
            profile.setTotalExperienceYears(exp != null ? new BigDecimal(exp.toString()) : null);
        }
        if (request.containsKey("primary_skills")) profile.setPrimarySkills(objectMapper.writeValueAsString(request.get("primary_skills")));
        if (request.containsKey("current_salary")) profile.setCurrentSalary((String) request.get("current_salary"));
        if (request.containsKey("salary_expectation")) profile.setSalaryExpectation((String) request.get("salary_expectation"));
        if (request.containsKey("notice_period")) profile.setNoticePeriod((String) request.get("notice_period"));
        if (request.containsKey("reason_for_leaving")) profile.setReasonForLeaving((String) request.get("reason_for_leaving"));
        if (request.containsKey("career_gap_details")) profile.setCareerGapDetails((String) request.get("career_gap_details"));
        if (request.containsKey("highest_education")) profile.setHighestEducation((String) request.get("highest_education"));
        if (request.containsKey("preferred_locations")) profile.setPreferredLocations(objectMapper.writeValueAsString(request.get("preferred_locations")));
        if (request.containsKey("preferred_role")) profile.setPreferredRole((String) request.get("preferred_role"));
        if (request.containsKey("additional_notes")) profile.setAdditionalNotes((String) request.get("additional_notes"));

        CandidateProfile saved = profileRepository.save(profile);
        return ResponseEntity.ok(saved);
    }
}
