package com.asknehru.interviewsimulator.interview;

import com.asknehru.interviewsimulator.interview.Answer;
import com.asknehru.interviewsimulator.interview.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Optional<Answer> findTopByQuestionOrderByIdDesc(Question question);
}
