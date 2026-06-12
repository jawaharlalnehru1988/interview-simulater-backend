package com.asknehru.interviewsimulator.hrvoice;

import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewSession;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HRVoiceInterviewSessionRepository extends JpaRepository<HRVoiceInterviewSession, Long> {
    List<HRVoiceInterviewSession> findByUserOrderByUpdatedAtDesc(User user);
}
