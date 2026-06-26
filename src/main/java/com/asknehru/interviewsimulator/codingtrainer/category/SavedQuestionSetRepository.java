package com.asknehru.interviewsimulator.codingtrainer.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedQuestionSetRepository extends JpaRepository<SavedQuestionSet, Long> {
    List<SavedQuestionSet> findByTopicAndCategory(String topic, String category);
}
