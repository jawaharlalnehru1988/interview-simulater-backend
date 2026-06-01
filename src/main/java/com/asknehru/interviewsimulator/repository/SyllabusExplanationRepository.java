package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Syllabus;
import com.asknehru.interviewsimulator.model.SyllabusExplanation;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SyllabusExplanationRepository extends JpaRepository<SyllabusExplanation, Long> {
    Optional<SyllabusExplanation> findByUserAndSyllabusAndTopicAndSubtopic(User user, Syllabus syllabus, String topic, String subtopic);
    List<SyllabusExplanation> findBySyllabusAndUserOrderByTopicAscSubtopicAsc(Syllabus syllabus, User user);
}
