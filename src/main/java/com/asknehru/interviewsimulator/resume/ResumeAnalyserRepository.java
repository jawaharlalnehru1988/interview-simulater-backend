package com.asknehru.interviewsimulator.resume;

import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeAnalyserRepository extends JpaRepository<ResumeAnalyser, Long> {
    @EntityGraph(attributePaths = {"questions", "questions.generatedAnswers"})
    List<ResumeAnalyser> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {"questions", "questions.generatedAnswers"})
    Optional<ResumeAnalyser> findById(Long id);
}
