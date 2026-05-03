package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.JobDescriptionAnalysis;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobDescriptionAnalysisRepository extends JpaRepository<JobDescriptionAnalysis, Long> {
    List<JobDescriptionAnalysis> findByUserOrderByUpdatedAtDesc(User user);
}
