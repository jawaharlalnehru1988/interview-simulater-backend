package com.asknehru.interviewsimulator.hrvoice;

import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewSession;
import com.asknehru.interviewsimulator.hrvoice.HRVoiceInterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HRVoiceInterviewTurnRepository extends JpaRepository<HRVoiceInterviewTurn, Long> {
    List<HRVoiceInterviewTurn> findBySessionOrderByCreatedAtAsc(HRVoiceInterviewSession session);
}
