package com.asknehru.interviewsimulator.document;

import com.asknehru.interviewsimulator.document.JobDescriptionAnalysis;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobDescriptionAnalysisRepository extends JpaRepository<JobDescriptionAnalysis, Long> {
    List<JobDescriptionAnalysis> findByUserOrderByUpdatedAtDesc(User user);
}
