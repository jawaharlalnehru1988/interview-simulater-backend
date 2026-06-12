package com.asknehru.interviewsimulator.syllabus;

import com.asknehru.interviewsimulator.syllabus.Syllabus;
import com.asknehru.interviewsimulator.syllabus.SyllabusExplanation;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SyllabusExplanationRepository extends JpaRepository<SyllabusExplanation, Long> {
    Optional<SyllabusExplanation> findByUserAndSyllabusAndTopicAndSubtopic(User user, Syllabus syllabus, String topic, String subtopic);
    List<SyllabusExplanation> findBySyllabusAndUserOrderByTopicAscSubtopicAsc(Syllabus syllabus, User user);
    void deleteBySyllabus(Syllabus syllabus);
}
