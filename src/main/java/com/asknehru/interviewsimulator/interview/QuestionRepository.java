package com.asknehru.interviewsimulator.interview;

import com.asknehru.interviewsimulator.interview.Interview;
import com.asknehru.interviewsimulator.interview.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByInterviewOrderByOrderAscCreatedAtAsc(Interview interview);
    List<Question> findByInterviewOrderByOrderDescCreatedAtDesc(Interview interview);
    Optional<Question> findFirstByInterviewAndOrder(Interview interview, Integer order);
}
