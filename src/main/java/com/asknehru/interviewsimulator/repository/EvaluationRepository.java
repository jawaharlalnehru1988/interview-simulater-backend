package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Answer;
import com.asknehru.interviewsimulator.model.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Evaluation findByAnswer(Answer answer);
}
