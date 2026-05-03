package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Answer;
import com.asknehru.interviewsimulator.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Optional<Answer> findByQuestion(Question question);
}
