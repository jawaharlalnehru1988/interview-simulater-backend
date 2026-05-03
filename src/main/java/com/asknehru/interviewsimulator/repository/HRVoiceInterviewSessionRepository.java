package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.HRVoiceInterviewSession;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HRVoiceInterviewSessionRepository extends JpaRepository<HRVoiceInterviewSession, Long> {
    List<HRVoiceInterviewSession> findByUserOrderByUpdatedAtDesc(User user);
}
