package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Interview;
import com.asknehru.interviewsimulator.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByInterviewOrderByOrderAscCreatedAtAsc(Interview interview);
    List<Question> findByInterviewOrderByOrderDescCreatedAtDesc(Interview interview);
    Optional<Question> findFirstByInterviewAndOrder(Interview interview, Integer order);
}
