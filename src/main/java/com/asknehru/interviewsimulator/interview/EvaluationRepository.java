package com.asknehru.interviewsimulator.interview;

import com.asknehru.interviewsimulator.interview.Answer;
import com.asknehru.interviewsimulator.interview.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Evaluation findByAnswer(Answer answer);
}
