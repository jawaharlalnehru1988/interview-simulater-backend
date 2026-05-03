package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.HRVoiceInterviewSession;
import com.asknehru.interviewsimulator.model.HRVoiceInterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HRVoiceInterviewTurnRepository extends JpaRepository<HRVoiceInterviewTurn, Long> {
    List<HRVoiceInterviewTurn> findBySessionOrderByCreatedAtAsc(HRVoiceInterviewSession session);
}
