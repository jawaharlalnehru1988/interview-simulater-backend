package com.asknehru.interviewsimulator.resume;

import com.asknehru.interviewsimulator.auth.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeStorageService {
    private final ResumeAnalyserRepository repository;

    @Transactional
    public ResumeAnalyser saveAnalysis(User user, String filename, String text, List<ResumeAnalyzerController.QuestionDto> dtos) {
        ResumeAnalyser analysis = ResumeAnalyser.builder()
                .user(user)
                .resumeFilename(filename)
                .extractedText(text)
                .build();

        if (dtos != null) {
            List<QuestionOnResume> questions = dtos.stream().map(dto -> {
                QuestionOnResume q = QuestionOnResume.builder()
                        .resume(analysis)
                        .category(dto.category())
                        .difficulty(dto.difficulty())
                        .question(dto.question())
                        .hint(dto.hint())
                        .build();

                if (dto.answers() != null && !dto.answers().isEmpty()) {
                    GeneratedAnswers ans = buildGeneratedAnswers(q, dto.answers());
                    q.setGeneratedAnswers(ans);
                }
                return q;
            }).toList();
            analysis.setQuestions(questions);
        }
        return repository.save(analysis);
    }

    @Transactional
    public Optional<ResumeAnalyser> updateAnalysis(Long id, User user, List<ResumeAnalyzerController.QuestionDto> dtos) {
        return repository.findById(id).filter(a -> a.getUser().getId().equals(user.getId())).map(analysis -> {
            analysis.getQuestions().clear();
            if (dtos != null) {
                List<QuestionOnResume> newQuestions = dtos.stream().map(dto -> {
                    QuestionOnResume q = QuestionOnResume.builder()
                            .resume(analysis)
                            .category(dto.category())
                            .difficulty(dto.difficulty())
                            .question(dto.question())
                            .hint(dto.hint())
                            .build();

                    if (dto.answers() != null && !dto.answers().isEmpty()) {
                        GeneratedAnswers ans = buildGeneratedAnswers(q, dto.answers());
                        q.setGeneratedAnswers(ans);
                    }
                    return q;
                }).toList();
                analysis.getQuestions().addAll(newQuestions);
            }
            return repository.save(analysis);
        });
    }

    @Transactional(readOnly = true)
    public List<ResumeAnalyser> getAnalysesByUser(User user) {
        return repository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void saveGeneratedAnswers(Long analysisId, User user, String questionText, JsonNode answersNode) {
        repository.findById(analysisId).ifPresent(analysis -> {
            if (!analysis.getUser().getId().equals(user.getId())) return;
            for (QuestionOnResume q : analysis.getQuestions()) {
                if (q.getQuestion().equals(questionText)) {
                    List<String> stringAnswers = new java.util.ArrayList<>();
                    if (answersNode.isArray()) {
                        for (JsonNode n : answersNode) {
                            stringAnswers.add(n.asText());
                        }
                    }
                    GeneratedAnswers newAnswers = buildGeneratedAnswers(q, stringAnswers);
                    q.setGeneratedAnswers(newAnswers);
                    break;
                }
            }
            repository.save(analysis);
        });
    }

    private GeneratedAnswers buildGeneratedAnswers(QuestionOnResume q, List<String> strings) {
        GeneratedAnswers ans = GeneratedAnswers.builder().question(q).build();
        if (strings.size() > 0) ans.setAnswer1(strings.get(0));
        if (strings.size() > 1) ans.setAnswer2(strings.get(1));
        if (strings.size() > 2) ans.setAnswer3(strings.get(2));
        if (strings.size() > 3) ans.setAnswer4(strings.get(3));
        if (strings.size() > 4) ans.setAnswer5(strings.get(4));
        return ans;
    }
}
