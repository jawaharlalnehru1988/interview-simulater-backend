package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.*;
import com.asknehru.interviewsimulator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProgressService {

    private final InterviewRepository interviewRepository;
    private final PersonalCoachSessionRepository coachRepository;
    private final UserAspirationRepository aspirationRepository;
    private final JobDescriptionAnalysisRepository jdRepository;

    public Map<String, Object> getUserProgress(User user) {
        Map<String, Object> progress = new HashMap<>();

        // Modules Summary
        Map<String, Object> modules = new HashMap<>();
        
        List<Interview> interviews = interviewRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("interview", interviews.stream().map(this::mapInterview).collect(Collectors.toList()));
        
        List<PersonalCoachSession> coachSessions = coachRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("personal_coach", coachSessions.stream().map(this::mapCoach).collect(Collectors.toList()));
        
        List<UserAspiration> aspirations = aspirationRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("aspirations", aspirations.stream().map(this::mapAspiration).collect(Collectors.toList()));
        
        List<JobDescriptionAnalysis> jdAnalyses = jdRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("job_description_analyzer", jdAnalyses.stream().map(this::mapJd).collect(Collectors.toList()));

        progress.put("modules", modules);

        // Attempted Topics
        Map<String, List<String>> attemptedTopics = new HashMap<>();
        attemptedTopics.put("interview_topics", interviews.stream().map(Interview::getTopic).distinct().collect(Collectors.toList()));
        attemptedTopics.put("personal_coach_topics", coachSessions.stream().map(PersonalCoachSession::getTopic).distinct().collect(Collectors.toList()));
        progress.put("attempted_topics", attemptedTopics);

        return progress;
    }

    private Map<String, Object> mapInterview(Interview i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", i.getId());
        map.put("topic", i.getTopic());
        map.put("round_type", i.getRoundType());
        map.put("status", i.getStatus());
        map.put("updated_at", i.getUpdatedAt());
        return map;
    }

    private Map<String, Object> mapCoach(PersonalCoachSession s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("topic", s.getTopic());
        map.put("stage", s.getStage());
        map.put("mastery_score", s.getMasteryScore());
        map.put("updated_at", s.getUpdatedAt());
        return map;
    }

    private Map<String, Object> mapAspiration(UserAspiration a) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", a.getId());
        map.put("target_job", a.getTargetJob());
        map.put("updated_at", a.getUpdatedAt());
        return map;
    }

    private Map<String, Object> mapJd(JobDescriptionAnalysis j) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", j.getId());
        map.put("company_name", j.getCompanyName());
        map.put("recruiter_name", j.getRecruiterName());
        map.put("updated_at", j.getUpdatedAt());
        return map;
    }
}
