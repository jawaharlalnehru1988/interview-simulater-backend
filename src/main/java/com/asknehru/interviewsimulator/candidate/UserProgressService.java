package com.asknehru.interviewsimulator.candidate;
import com.asknehru.interviewsimulator.interview.AnswerRepository;
import com.asknehru.interviewsimulator.interview.InterviewRepository;
import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewTurnRepository;
import com.asknehru.interviewsimulator.document.JobDescriptionAnalysisRepository;
import com.asknehru.interviewsimulator.interview.QuestionRepository;
import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewSessionRepository;
import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewTurn;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.interview.Question;
import com.asknehru.interviewsimulator.interview.Answer;
import com.asknehru.interviewsimulator.document.JobDescriptionAnalysis;
import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewSession;
import com.asknehru.interviewsimulator.interview.Interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProgressService {

    private final InterviewRepository interviewRepository;
    private final UserAspirationRepository aspirationRepository;
    private final JobDescriptionAnalysisRepository jdRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final HRVoiceInterviewSessionRepository hrSessionRepository;
    private final HRVoiceInterviewTurnRepository hrTurnRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> getUserProgress(User user) {
        Map<String, Object> progress = new HashMap<>();

        // Modules Summary
        Map<String, Object> modules = new HashMap<>();
        
        List<Interview> interviews = interviewRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("interview", interviews.stream().map(this::mapInterview).collect(Collectors.toList()));
        
        List<UserAspiration> aspirations = aspirationRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("aspirations", aspirations.stream().map(this::mapAspiration).collect(Collectors.toList()));
        
        List<JobDescriptionAnalysis> jdAnalyses = jdRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("job_description_analyzer", jdAnalyses.stream().map(this::mapJd).collect(Collectors.toList()));

        List<HRVoiceInterviewSession> hrSessions = hrSessionRepository.findByUserOrderByUpdatedAtDesc(user);
        modules.put("hr_voice_calls", hrSessions.stream().map(this::mapHrVoiceCall).collect(Collectors.toList()));

        progress.put("modules", modules);

        // Attempted Topics
        Map<String, List<String>> attemptedTopics = new HashMap<>();
        attemptedTopics.put("interview_topics", interviews.stream().map(Interview::getTopic).distinct().collect(Collectors.toList()));
        progress.put("attempted_topics", attemptedTopics);

        return progress;
    }

    private Map<String, Object> mapInterview(Interview i) {
        List<Question> questions = questionRepository.findByInterviewOrderByOrderDescCreatedAtDesc(i);
        int totalScore = 0;
        int evaluatedCount = 0;
        for (Question q : questions) {
            Optional<Answer> ansOpt = answerRepository.findTopByQuestionOrderByIdDesc(q);
            if (ansOpt.isPresent() && ansOpt.get().getEvaluation() != null) {
                totalScore += ansOpt.get().getEvaluation().getScore();
                evaluatedCount++;
            }
        }
        Integer averageScore = evaluatedCount > 0 ? (totalScore / evaluatedCount) : null;

        Map<String, Object> map = new HashMap<>();
        map.put("interview_id", i.getId());
        map.put("topic", i.getTopic());
        map.put("round", i.getRoundType() != null ? i.getRoundType().name() : "");
        map.put("status", i.getStatus() != null ? i.getStatus().name() : "");
        map.put("questions_asked", questions.size());
        map.put("average_score", averageScore);
        map.put("last_updated", i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> mapAspiration(UserAspiration a) {
        Integer readinessScore = null;
        String summary = "";
        try {
            if (a.getRoadmap() != null && !a.getRoadmap().isEmpty()) {
                JsonNode node = objectMapper.readTree(a.getRoadmap());
                readinessScore = node.path("readiness_score").asInt();
                summary = node.path("summary").asText("");
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> map = new HashMap<>();
        map.put("aspiration_id", a.getId());
        map.put("current_position", a.getCurrentPosition() != null ? a.getCurrentPosition() : "");
        map.put("target_job", a.getTargetJob() != null ? a.getTargetJob() : "");
        map.put("timeline_months", a.getTimelineMonths() != null ? a.getTimelineMonths() : 6);
        map.put("readiness_score", readinessScore);
        map.put("summary", summary);
        map.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
        map.put("last_updated", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> mapJd(JobDescriptionAnalysis j) {
        String recruiterIntent = "";
        try {
            if (j.getAnalysis() != null && !j.getAnalysis().isEmpty()) {
                JsonNode node = objectMapper.readTree(j.getAnalysis());
                recruiterIntent = node.path("recruiter_intent").asText("");
            }
        } catch (Exception e) {
            // ignore
        }

        String preview = j.getJobDescription() != null ? j.getJobDescription() : "";
        if (preview.length() > 100) {
            preview = preview.substring(0, 97) + "...";
        }

        Map<String, Object> map = new HashMap<>();
        map.put("analysis_id", j.getId());
        map.put("company_name", j.getCompanyName() != null ? j.getCompanyName() : "");
        map.put("recruiter_name", j.getRecruiterName() != null ? j.getRecruiterName() : "");
        map.put("application_last_date", j.getApplicationLastDate() != null ? j.getApplicationLastDate().toString() : null);
        map.put("application_last_date_raw", j.getApplicationLastDateRaw() != null ? j.getApplicationLastDateRaw() : "");
        map.put("job_description_preview", preview);
        map.put("recruiter_intent", recruiterIntent);
        map.put("created_at", j.getCreatedAt() != null ? j.getCreatedAt().toString() : "");
        map.put("last_updated", j.getUpdatedAt() != null ? j.getUpdatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> mapHrVoiceCall(HRVoiceInterviewSession s) {
        List<HRVoiceInterviewTurn> turns = hrTurnRepository.findBySessionOrderByCreatedAtAsc(s);
        
        List<String> questionsList = List.of();
        try {
            if (s.getQuestions() != null) {
                questionsList = objectMapper.readValue(s.getQuestions(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {}

        Double averageScore = null;
        if (!turns.isEmpty()) {
            averageScore = turns.stream().mapToInt(HRVoiceInterviewTurn::getScore).average().orElse(0);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("session_id", s.getId());
        map.put("status", s.getStatus() != null ? s.getStatus().name() : "");
        map.put("question_count", questionsList.size());
        map.put("answered_count", turns.size());
        map.put("average_score", averageScore);
        map.put("pass_decision", s.getPassDecision());
        map.put("target_job", s.getAspiration() != null ? s.getAspiration().getTargetJob() : "");
        map.put("company_name", s.getJobAnalysis() != null ? s.getJobAnalysis().getCompanyName() : "");
        map.put("last_updated", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : "");
        return map;
    }
}
